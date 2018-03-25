# Chaos

_External BGP-4 Simulator with Lightweight Traffic Simulation_

**NOTE**: This is the public version of Chaos. Work is being done on a private branch that will be pushed when updates are ready and (mostly) tested.

## Purpose

Chaos, formerly known as _Nightwing_, is a general-purpose BGP simulator, with the capability to mirror the entire known Internet (that is, all known ASes), and run large-scale simulations across all ASes with tweaked routing policies and traffic movements.

To that end, Chaos is _the foundation_ for the evaluation of other VolSec projects concerning Internet resiliency.

## Systems Evaluated with Chaos
- **Nyx**: DDoS Mitigation by Routing Around Congestion
- **E-Embargos**: Economic Costs of Decoy Routing
- **LCI**: Losing Control of the Internet via Strategic Attacks on the BGP Routing Infrastructure

## Documentation

- Function comments are few and far between, but will be added as time goes on.
- Documentation will eventually be in the `docs/` folder.

## Branches
- **master**: Java version of the simulator. This has been used for nearly a decade, and is currently the base for all evaluated systems. This branch is considered to be stable (in most cases!). If a major flaw is found, please raise an issue, which will then be addressed.
- **develop**: Development version of the master branch. This branch is private and will be used for work to be done before being made public.

## Choice of Name

According to Greek Mythology, Chaos was the first "being" to exist: "_at first Chaos came to be_". The first new project under Chaos after it's transformation from the name of _Nightwing_, was _Nyx_. Nyx is the Greek Goddess of **Night**, and even older than the titans themselves. Specifically, Nyx was unambiguously born "_from Chaos_" along with _Erebus_. Before the simulator was refactored to be independent of the system evaluations, Nyx also contained the main simulator. And before it was named Nyx, it was Nightwing, which the name Nyx was inspired by.
