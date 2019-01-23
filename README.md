# Chaos

_External BGP-4 Simulator with Generic Traffic Flow Simulation_

## Systems Evaluated with Chaos
- **FRRPDDoS**: Carrying out Transit-Link DDos with FRRP
- **Nyx**: DDoS Mitigation with FRRP by Routing Around Congestion
- **E-Embargos**: Economic Costs of Decoy Routing
- **LCI**: Losing Control of the Internet via Strategic Attacks on the BGP Routing Infrastructure

## Branches
- **master**: Java version of the simulator. This has been used for nearly a decade, and is currently the base for all evaluated systems. This branch is considered to be stable. If a major flaw is found, please raise an issue, which will then be addressed.
- **develop**: Development version of the master branch. Not guaranteed to be fully functional or even compile or run
 a system evaluation.

## Choice of Name

According to Greek Mythology, Chaos was the first "being" to exist: "_at first Chaos came to be_". The first new project under Chaos after it's transformation from the name of _Nightwing_, was _Nyx_. Nyx is the Greek Goddess of **Night**, and even older than the titans themselves. Specifically, Nyx was unambiguously born "_from Chaos_" along with _Erebus_. Before the simulator was refactored to be independent of the system evaluations, Nyx also contained the main simulator. And before it was named Nyx, it was Nightwing, which the name Nyx was inspired by.

---

# Guide to Chaos

## Context

> What is Chaos and what's it for?

Chaos is an AS-Level E-BGP, not I-BGP, simulation framework, with the included capability to ingest various 
topological, bandwidth, adversarial (i.e. botnet), and other arbitrary models and drive experimentation with 
topologies on the scale of the modern Internet. Additionally, Chaos can track generic units of "traffic" across 
AS-to-AS links, which enables tracking congestion (useful for DDoS research).

> How does it fit into the broader scope of BGP and traffic simulators?

Chaos is not the first BGP simulator by any stretch; however, it enables much more rapid iteration on topologies at 
the scale of the modern Internet, without either 1) paying for commericial simulators, or 2) being restricted to 
small topologies. Simulation environments like GNS3, OmniNet with BGP++, and others are feature-complete BGP 
simulators, though they suffer from lack of scalability or cost money. C-BGP comes closest to Chaos, though lacks an 
intuitive binary interface, and must be manipulated via the command-line.

> Who's using it and who has used it?

Chaos is based on Max Schuchard's BGP simulator from his PhD at the University of Minnesota, and has been built on 
for work by the UT Computer Security Lab, VolSec.

## Functional Overview

> What does this system actually do?

Chaos has two primary purposes:

1. Simulate ASes as single objects, modeling their interaction via BGP, with included traffic flow
2. Enable experiments to build off #1.

The BGP simulation is modeled as a steady state, and not inherently real-time.

> Where are these components kept in the code?

1. Contained within `src.main.java.chaos.sim` and `src.main.java.chaos.topo`
2. Contained within `src.main.java.chaos.eval.*` with `src` folders for each experiment, initiated from #1 `sim.Main`.

> What are the performance expectations?

To build the BGP topology and initialize AS-to-AS link capacities, startup time on machines with 50+ cores ranges 
from 10 to 20 minutes, and consumes 200+ GB of RAM with the full topology and AS-to-AS properties stored in memories,
 included both the best current paths and all available paths between every AS.

## Constraints

> What **can't** Chaos do?

Chaos can't currently do any of the following:

- Simulate BGP at the AS prefix level
- Represent traffic values as real-valued units, like a TCP packet
- Actually simulate TCP or UDP
- Carry out the **entire** BGP decision making process
    - In simulation, we don't need many parts, like MED.
    
Chaos needs a powerful machine with both available cores **and** large amounts of RAM.

> Is this code what a certified, bonifide software architect with 30 years of experience building 
planetary-scale systems at Google would produce?

No. Absolutely not. This is research code, and it could use some tender loving care to be made actually object-oriented
 and not consume ridiculous amounts of RAM. But, it works, and it works well. To quote Max Schuchard: _"We're computer
  scientists, not software engineers."_
 

## Software Architecture

### High-Level Overview

When Chaos is run, the flow is roughly as follows:

1. `chaos.sim.Chaos` is called, builds the topology and all bandwidth models, with data and configuration pulled from 
the `config` directory and the `data` directory. The topology is stored and passed around with the main simulator 
instance. The topology is seperated into an **active** topology, or all the ASes with customers, and the **pruned** 
or **purged** topology, which live on the edge, and must be extracted to preserve memory.
2. The main simulation class instance calls a specific `chaos.eval.src.EXP_NAME` class and manager, which then takes 
over as the main experiment, using the built topology.
3. When the experiment takes over, they interface back with the main simulation instance, and in particular, can call
 methods from the `chaos.topo.AS` class on individual ASes to make changes to things like advertisements for that AS.
  Then at any point, to reprocess BGP, these experiments will call the stored `chaos.sim.BGPMaster.driveBGPProcessing` 
  method to reprocess BGP.
4. These experiments can also call the `chaos.sim.TrafficManager` class and its associated traffic flow methods to 
recompute traffic flows based on changes in the BGP topology.
5. Finally, these experiments will collect data and log it out to flat files (and at one point also logged to MongoDB).

### Components and Class Breakdown

For more specific details about various methods and classes, see the comments per file for packages and shown files
 below.

#### Simulation Framework

**chaos.sim**:

This is the main simulation package that has the BGP topology processor, the traffic manager, and the main simulation
 entry point.

**chaos.topo**:

This package contains the main parts of the BGP topology, including the AS-level objects, which almost everything 
touches. The `AS` class also stores the traffic information between any two ASes. Finally, this class enables FRRP 
via the `moveTrafficOffLink` method.

**chaos.utils**, **chaos.logging**, **chaos.graphTheory**, **chaos.parsing**:

These directories contain various utility files for the simulation, parsing methods that take data from the local 
`config` directory and build various topologies, botnet models, bandwidth models, etc.

#### Evaluation/Experiment Framework

**chaos.eval**:

This is the Java packages that store individual experiments. Each named folder underneath here has a `src` folder 
that contains the `src` for that experiment. We will now breakdown the __Nyx__ experiment framework:

- `ReactiveEngine`: this contains the main **manager* for the Nyx experiment, and the experiment logic. The main 
experiment that should be studied and understood, and was used for the Routing Around Congestion paper at IEEE SP 
2018 starts with the method `manageReactiveFullTest`. This can simulate both **transit-link** and **traditional** 
DDoS. You will see if/else structures for this everywhere.
- `DisturbanceRunner` and `DisturbanceStats`: these aggregate and collect statistics on how effective techniques like 
selective advertisement and path lining work with FRRP, through the lense of how many ASes pickup and shift  paths 
when a deploying AS sends out a poisoned advertisements (see the System Methodology section of the Routing Around 
Congestion paper).
- `DisturbanceCollectorTask` and `DisturbanceCompareTask`: these are parallel job workers used by the Disturbance 
runners.
- `BookmarkInfo`: once upon a time the simulator would crash often and repeatedly, so this was historically used to 
bookmark runs. This should **not** be needed anymore.
- `DeployerCriticalLink` and `DeployerCriticalPair`: stores data about deployer and critical ASes, essential to Nyx 
keeping track of results.
- `ScenarioInfo`: internal data structure used to track the 3 main experiments in the original Nyx paper, 1) doing 
FRRP without disturbance mitigation, 2) doing FRRP with selective advertisement, and 3) doing FRRP with selective 
advertisement and path lining. Scenario 1 also originally used bot thresholds to limit the amount of bots used and 
their locations in the topology to congest targeted links.
- `ModifiedTestPathInfo`: Stores data about the new paths taken after FRRP is employed by the deployer.
- `BandwidthScenarioInfo`: Stores data about the link capacity and congestion on links at any given time.
- `future/`: stores some of the early work (which should be thrown out) Jared did on extending the simulation to the 
next  phase (multiple deployers and adversaries).

**chaos.eval.utils**:

These packages contain utilities used by many of the experiment/eval packages.

- `BotCollectorTask` and `BotContainerTask`: though these sound specific, they essentially check whether some bot AS 
can hit the current best path between the deployer and critical AS, and do so in parallel. This can be generified for
 any specific path.
- `BotTargetingDeployerCollectorTask`: does the same as the prior but for a DDoS attack when the botnet ASes target 
the deployer directly.
- `future/`: unused files from early work on v2.

## Installing and Running Chaos

Chaos relies on the following dependencies (for VolSec, all exist on our internal server)

- Java 8+
- Maven (which will pull other packages)
- Python 2.7.X or 3.4.X

Chaos is packaged and ran with Maven, via `mvn`. However, Chaos and the currently implemented experiments can be 
controlled with `run.py` and Python in the root directory. Before running however, create the `logs` directory in the
 root to store experiment results.
 
In particular, to  run  the Nyx experiment for 10,000 runs, with the config specified 
in the `config/default_config.yml` file, where the topology, botnet model, and bandwidth models are all specified in 
that config file, and naming the log/results files TEST_NYX you can run:

```$bash
python run.py --numRuns 10000 --logId TEST_NYX \
              --config config/default_config.yml \
              --jarFile target/chaos-1.0-SNAPSHOT-jar-with-dependencies.jar \
              --full
```
