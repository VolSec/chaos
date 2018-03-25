package chaos.sim;

import chaos.eval.nyx.src.ReactiveEngine;
import chaos.logging.ThreadedWriter;
import chaos.topo.AS;
import chaos.topo.SerializationMaster;
import com.esotericsoftware.yamlbeans.YamlReader;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import gnu.trove.map.TIntObjectMap;
import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.impl.Arguments;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;
import org.bson.Document;

import java.io.*;
import java.util.Set;

public class Chaos implements Runnable {

    public enum EvalMode {
        NYX
    }

    public enum EvalSim {
        TEST_REACTIVE, FULL_REACTIVE, MID_LEVEL_TEST, DISTURBANCE_AVOID, TARGET_REACTOR,
    }

    private Namespace cliArgs;
    private Config config;
    private String resistorFile;
    private Writer generalOut;
    private Boolean parallelLogging;
    private MongoClient mongoClient;
    public MongoDatabase mongoDatabase;
    public EvalSim evalSim;
    private EvalMode evalMode;
    private TIntObjectMap<AS> activeTopology;
    private TIntObjectMap<AS> prunedTopology;
    private Set<Double> bandwidthTolerances;
    private BGPMaster bgpMaster;
    private ReactiveEngine reactiveManager;
    private AS.AvoidMode resistorStrat;
    private AS.ReversePoisonMode reverseStrat;
    private String logDirString;
    private PerformanceLogger perfLogger;

    private static long LARGE_MEM_THRESH = (long) 1024 * (long) 1024 * (long) 1024 * (long) 100;

    public Chaos(Namespace ns) throws IOException {
        /*
         * Store the name space, as optional args might need to be fetched later
         */
        this.cliArgs = ns;

        /*
         * Read in config file
         */
        YamlReader reader = new YamlReader(new FileReader(this.cliArgs.getString("config")));
        this.config = reader.read(Config.class);

        /*
         * Set the main eval mode
         */
        this.evalMode = ns.get("mode");

        /*
         * Load in the required arguments from name space
         */
        this.evalSim = ns.get("sim");
        this.parallelLogging = this.config.parallelLogging;
        this.logDirString = this.buildLoggingPath(ns);

        if (this.parallelLogging) {
            this.generalOut = new ThreadedWriter(this.logDirString + "general.log");
            Thread generalOutThread = new Thread((ThreadedWriter) this.generalOut, "General Output Thread");
            generalOutThread.start();
        } else {
            this.generalOut = new BufferedWriter(new FileWriter(this.logDirString + "general.log"));
        }

        boolean largeMemoryEnv = Runtime.getRuntime().maxMemory() >= Chaos.LARGE_MEM_THRESH;
        this.logMessage("Large memory env: " + largeMemoryEnv);
        if (ns.getBoolean("forceLowMem")) {
            this.logMessage("Overriding memory environment FORCING LOW");
            largeMemoryEnv = false;
        }

        /*
         * Build objects required
         */
        SerializationMaster serialControl = new SerializationMaster(this);
        this.perfLogger = new PerformanceLogger(this.logDirString, "general_perf.log");
        this.bgpMaster = new BGPMaster(this);
        this.bgpMaster.setPerfLog(this.perfLogger);

        /*
         * Bootstrap our topology
         */
        this.logMessage("Starting initial build of BGP topo...");
        TIntObjectMap<AS>[] fullTopology;
        fullTopology = this.bgpMaster.buildBGPConnection(largeMemoryEnv);

        this.activeTopology = fullTopology[0];
        this.prunedTopology = fullTopology[1];
        this.calculateCustomerCone(this.activeTopology, this.prunedTopology);
        this.logMessage("Topology built and BGP converged.");

        this.bandwidthTolerances = this.config.bandwidthTolerances;
        TrafficManager trafficManager = new TrafficManager(this, this.activeTopology, this.prunedTopology, serialControl, perfLogger);

        if (this.config.computeBandwidth) {
            this.logMessage("Building initial traffic and bandwidth model...");
            trafficManager.runFullTrafficAndBandwidthComputation(bandwidthTolerances);
            this.logMessage("Done building initial traffic and bandwidth model.");
        }

        if (this.config.ASDataCollection) {
            this.logASInformation();
            System.exit(0);
        }

        if (this.config.capacityDataCollection) {
            this.logCapacityInformation();
            System.exit(0);
        }

        this.logMessage("Making bandwidth tolerance to capacity maps unmodifiable...");
        for (AS AS : this.activeTopology.valueCollection()) {
            AS.makeLinkCapacitiesMapUnmodifiable();
        }

        for (AS AS : this.prunedTopology.valueCollection()) {
            AS.makeLinkCapacitiesMapUnmodifiable();
        }
        this.logMessage("Done making bandwidth tolerance to capacity maps unmodifiable.");

        if (this.evalMode == EvalMode.NYX) {
            this.reactiveManager = new ReactiveEngine(this, this.activeTopology, this.prunedTopology,
                    this.logDirString, this.perfLogger, this.bandwidthTolerances,
                    trafficManager);
        }
    }

    public void run() {
        this.logMessage("Starting actual simulation...");
        long startTime = System.currentTimeMillis();

        if (this.evalSim == EvalSim.FULL_REACTIVE) {
            this.reactiveManager.manageReactiveFullTest(this.cliArgs.get("numRuns"), this.cliArgs.get("withBots"),
                    this.cliArgs.getBoolean("disturbance"), this.config.skipLogging, this.cliArgs.getBoolean("restarted"),
                    this.cliArgs.getBoolean("ignoreOnePathLimit"), this.cliArgs.getInt("restartNumRun"),
                    this.cliArgs.getBoolean("targetReactor"));
            this.reactiveManager.endSim();
        } else  {
            this.logMessage(this.evalSim + " not implemented fully!");
        }

        long endTime = System.currentTimeMillis();
        this.perfLogger.done();

        this.logMessage("\nSimulation done. Elapsed Time: " + (endTime - startTime) / 60000 + " minutes.");
    }

    public static void main(String[] args) throws IOException {
        ArgumentParser argParse = ArgumentParsers.newFor("Chaos").build()
                .defaultHelp(true)
                .description("External BGP4 and Lightweight Traffic Simulator");

        argParse.addArgument("-c", "--config").help("Config file").required(true);
        argParse.addArgument("-m", "--mode").help("Eval Mode").required(true).type(EvalMode.class);
        argParse.addArgument("-s", "--sim").help("Eval Sim").required(true).type(EvalSim.class);

        // Config for Embargo
        argParse.addArgument("-rs", "--reverse-strategy").help("reverse poisoning strat").required(false)
                .type(AS.ReversePoisonMode.class).setDefault(AS.ReversePoisonMode.LYING);
        argParse.addArgument("-wf", "--warden-file").help("Warden/Resistor file").required(false);
        argParse.addArgument("-ws", "--warden-strategy").help("Warden strategy").required(false)
                .type(AS.AvoidMode.class).setDefault(AS.AvoidMode.LOCALPREF);

        /*
         * Optional flags for reconfiguring simulation constants
         */
        argParse.addArgument("--forceLowMem").help("Forces simulator into low mem enviornment").required(false)
                .action(Arguments.storeTrue());
        argParse.addArgument("--nopathlog").help("Turns off path logging.").required(false)
                .action(Arguments.storeTrue());
        argParse.addArgument("--linklog").help("Turns on link load logging.").required(false)
                .action(Arguments.storeTrue());
        argParse.addArgument("--numRuns").help("Configure the number of runs to run an experiment").required(false)
                .type(Integer.class).setDefault(100);
        argParse.addArgument("--restarted").help("Indicate whether this is a restart of the server and we need to get" +
                "the last bookmark").required(false).action(Arguments.storeTrue()).setDefault(false);
        argParse.addArgument("--withBots").help("Turn on bots in the simulation").required(false)
                .action(Arguments.storeTrue()).setDefault();
        argParse.addArgument("--disturbance").help("Turn on disturbance data collection").required(false)
                .action(Arguments.storeTrue()).setDefault(true);
        argParse.addArgument("--restartId").help("ID for this particular restart").required(false)
                .type(Integer.class).setDefault(0);
        argParse.addArgument("--restartNumRun").help("If we can't count on the num run from the serial file, use this " +
                "one instead.").required(false).type(Integer.class).setDefault(0);
        argParse.addArgument("--logId").help("An ID to identify log files").required(false)
                .type(String.class).setDefault("");
        argParse.addArgument("--ignoreOnePathLimit").help("Ignore the requirement for selecting reacting and moved AS's" +
                " that there be more than one path between the two").required(false)
                .action(Arguments.storeTrue()).setDefault(false);
        argParse.addArgument("--targetReactor").help("Whether the botnet should target the reactor AS.").action(
                Arguments.storeTrue()).setDefault(false);


        argParse.addArgument("--coverageOrdering").help("tells simulator to use coverage ordering").required(false)
                .action(Arguments.storeTrue());
        argParse.addArgument("--deployers").help("deployer list file").required(false);
        argParse.addArgument("--defection").help("Turns on defection exploration").required(false)
                .action(Arguments.storeTrue());

        /*
         * Actually parse
         */
        Namespace ns = null;
        try {
            ns = argParse.parseArgs(args);
        } catch (ArgumentParserException e1) {
            argParse.handleError(e1);
            System.exit(-1);
        }

        Chaos self = new Chaos(ns);
        self.run();
    }

    /**
     * Function for writing a string to both the general output log and the console.
     *
     * @param message
     */
    public void logMessage(String message) {
        System.out.println(message);
        try {
            this.generalOut.write(message + '\n');
        } catch (IOException e) {
            System.out.println(e.toString());
            System.exit(1);
        }
    }

    private void logASInformation() {
        this.logMessage("Collecting AS data and saving it to mongo...");

        MongoCollection<Document> ASData = this.mongoDatabase.getCollection("ASData");

        // Delete existing data
        ASData.deleteMany(new Document());

        System.out.println("Collecting active AS data...");
        for (AS tAS : this.activeTopology.valueCollection()) {
            Document doc = new Document("asn", tAS.getASN())
                    .append("type", "active")
                    .append("ip_count", tAS.getIPCount())
                    .append("num_customers", tAS.getCustomers().size())
                    .append("customers_cone_size", tAS.getCustomerConeSize())
                    .append("customers_ip_cone_size", tAS.getIPCustomerCone())
                    .append("degree", tAS.getDegree())
                    .append("customers_non_pruned_size", tAS.getNonPrunedCustomerCount())
                    .append("num_peers", tAS.getPeers().size())
                    .append("num_providers", tAS.getProviders().size())
                    .append("num_active_neighbors", tAS.getActiveNeighbors().size())
                    .append("num_pruned_neighbors", tAS.getPurgedNeighbors().size())
                    .append("active_neighbors", tAS.getActiveNeighbors())
                    .append("pruned_neighbors", tAS.getPurgedNeighbors())
                    .append("bot_as", tAS.isBotAS())
                    .append("ip_percentage", tAS.getIPPercentage())
                    .append("in_rib_size", tAS.getInRib().size());
            ASData.insertOne(doc);
        }

        this.logMessage("Collecting pruned AS data...");
        for (AS tAS : this.prunedTopology.valueCollection()) {
            Document doc = new Document("asn", tAS.getASN())
                    .append("type", "pruned")
                    .append("ip_count", tAS.getIPCount())
                    .append("num_customers", tAS.getCustomers().size())
                    .append("customers_cone_size", tAS.getCustomerConeSize())
                    .append("customers_ip_cone_size", tAS.getIPCustomerCone())
                    .append("degree", tAS.getDegree())
                    .append("customers_non_pruned_size", tAS.getNonPrunedCustomerCount())
                    .append("num_peers", tAS.getPeers().size())
                    .append("num_providers", tAS.getProviders().size())
                    .append("num_active_neighbors", tAS.getActiveNeighbors().size())
                    .append("num_pruned_neighbors", tAS.getPurgedNeighbors().size())
                    .append("active_neighbors", tAS.getActiveNeighbors())
                    .append("pruned_neighbors", tAS.getPurgedNeighbors())
                    .append("bot_as", tAS.isBotAS())
                    .append("ip_percentage", tAS.getIPPercentage())
                    .append("in_rib_size", tAS.getInRib().size());
            ASData.insertOne(doc);
       }
    }

    private void logCapacityInformation() throws IOException {
        this.logMessage("Collecting capacity data and saving it to mongo and disk...");

        BufferedWriter capacityOut = new BufferedWriter(new FileWriter(this.logDirString + "capacities.txt"));

        MongoCollection<Document> CapacityData = this.mongoDatabase.getCollection("CapacityData");

        // Delete existing data
        CapacityData.deleteMany(new Document());

        for (AS tAS : this.getActiveTopology().valueCollection()) {
            for (int tNeighborASN : tAS.getNeighbors()) {
                for (Double bandwidthTolerance : this.bandwidthTolerances) {
                    Double capacity = tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance);
                    Document doc = new Document("asn", tAS.getASN())
                            .append("type", "active")
                            .append("AS1", tAS.getASN())
                            .append("AS2", tNeighborASN)
                            .append("capacity", capacity);
                    CapacityData.insertOne(doc);

                    capacityOut.write(String.format("Type-%s-AS1-%d-AS2-%d-Capacity-%f", "active", tAS.getASN(), tNeighborASN, capacity));
                    capacityOut.write("\n");
                }
            }
        }

        for (AS tAS : this.getPrunedTopology().valueCollection()) {
            for (int tNeighborASN : tAS.getNeighbors()) {
                for (Double bandwidthTolerance : bandwidthTolerances) {
                    Double capacity = tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance);
                    Document doc = new Document("asn", tAS.getASN())
                            .append("type", "pruned")
                            .append("AS1", tAS.getASN())
                            .append("AS2", tNeighborASN)
                            .append("capacity", capacity);
                    CapacityData.insertOne(doc);

                    capacityOut.write(String.format("Type-%s-AS1-%d-AS2-%d-Capacity-%f", "pruned", tAS.getASN(), tNeighborASN, capacity));
                    capacityOut.write("\n");
                }
            }
        }

        capacityOut.flush();
        capacityOut.close();

        this.logMessage("Done collecting capacity data.");
    }

    private String buildLoggingPath(Namespace ns) {

        String[] frags = null;
        System.out.println(this.evalMode);

        String outStr = this.config.baseLogDir;

        if (ns.getBoolean("defection")) {
            outStr += "DEFECTION";
        }

        /*
         * Encode deploy mode
         */
        if (this.evalSim == EvalSim.FULL_REACTIVE) {
            outStr += "FullReactive";
        } else if (this.evalSim == EvalSim.MID_LEVEL_TEST) {
            outStr += "MidLevelTest";
        } else if (this.evalSim == EvalSim.DISTURBANCE_AVOID) {
            outStr += "DisturbanceAvoid";
        } else if (this.evalSim == EvalSim.TEST_REACTIVE) {
            outStr += "InitialTest";
        }

        /*
         * Encode avoid mode
         */
        if (this.resistorStrat == AS.AvoidMode.LEGACY) {
            outStr += "Legacy";
        } else if (this.resistorStrat == AS.AvoidMode.LOCALPREF) {
            outStr += "LocalPref";
        } else if (this.resistorStrat == AS.AvoidMode.PATHLEN) {
            outStr += "PathLen";
        } else if (this.resistorStrat == AS.AvoidMode.TIEBREAK) {
            outStr += "Tiebreak";
        } else if (this.resistorStrat == AS.AvoidMode.NONE) {
            outStr += "None";
        }

        /*
         * Encode reverse mode
         */
        if (this.reverseStrat == AS.ReversePoisonMode.NONE) {
            outStr += "NoRev";
        } else if (this.reverseStrat == AS.ReversePoisonMode.LYING) {
            outStr += "Lying";
        } else if (this.reverseStrat == AS.ReversePoisonMode.HONEST) {
            outStr += "Honest";
        }

        outStr += "-NumRuns-" + this.cliArgs.get("numRuns");

        int restartId = this.cliArgs.getInt("restartId");
        if (!(restartId == 0)) {
            outStr += "-" + restartId;
        } else {
            outStr += "-" + "0";
        }

        String logId = this.cliArgs.getString("logId");
        if (!(logId.equals(""))) {
            outStr += "-" + logId;
        }

        /*
         * Make the directory we're going to log to
         */
        File tFileHook = new File(outStr);
        tFileHook.mkdir();

        /*
         * Dump the namespace to a file just in case
         */
        try {
            BufferedWriter outFP = new BufferedWriter(new FileWriter(outStr + "/ns.txt"));
            outFP.write(ns.toString());
            outFP.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        return outStr + "/";
    }

    /**
     * Calculate ccSize for each AS the start point of DFS.
     */
    private void calculateCustomerCone(TIntObjectMap<AS> activeTopology, TIntObjectMap<AS> prunedTopology) {
        /*
         * Do this over all ASes since we'll need the IP cust cones populated
         * for the econ part of the sim
         */
        for (AS tAS : activeTopology.valueCollection()) {
            this.buildIndividualCustomerCone(tAS);

            long ipCCSize = 0;
            double trafficFactorCC = 0.0;
            for (int tASN : tAS.getCustomerConeASList()) {
                AS customerConeAS = activeTopology.get(tASN);
                if (customerConeAS == null) {
                    customerConeAS = prunedTopology.get(tASN);
                }
                ipCCSize += customerConeAS.getIPCount();
                trafficFactorCC += customerConeAS.getBaseTrafficFactor();
            }
            tAS.setCustomerIPCone(ipCCSize);
            tAS.setCustomerTrafficCone(trafficFactorCC);
        }

        for (AS tAS : prunedTopology.valueCollection()) {
            this.buildIndividualCustomerCone(tAS);

            long ipCCSize = 0;
            double trafficFactorCC = 0.0;
            for (int tASN : tAS.getCustomerConeASList()) {
                AS customerConeAS = activeTopology.get(tASN);
                if (customerConeAS == null) {
                    customerConeAS = prunedTopology.get(tASN);
                }
                ipCCSize += customerConeAS.getIPCount();
                trafficFactorCC += customerConeAS.getBaseTrafficFactor();
            }
            tAS.setCustomerIPCone(ipCCSize);
            tAS.setCustomerTrafficCone(trafficFactorCC);
        }
    }

    /**
     * DFS the AS tree recursively by using the given AS as the root and get all
     * the ASes in its customer cone.
     */
    private void buildIndividualCustomerCone(AS currentAS) {

        /*
         * Skip ASes that have already been built at an earlier stage
         */
        if (currentAS.getCustomerConeSize() != 0) {
            return;
        }

        for (int tASN : currentAS.getPurgedNeighbors()) {
            currentAS.addOnCustomerConeList(tASN);
        }
        for (AS nextAS : currentAS.getCustomers()) {
            this.buildIndividualCustomerCone(nextAS);
            for (int tASN : nextAS.getCustomerConeASList()) {
                currentAS.addOnCustomerConeList(tASN);
            }
        }

        /* count itself */
        currentAS.addOnCustomerConeList(currentAS.getASN());
    }


    public String getLogDir() {
        return logDirString;
    }

    public Writer getGeneralOut() {
        return generalOut;
    }

    public Boolean getParallelLogging() {
        return this.parallelLogging;
    }

    public Set<Double> getBandwidthTolerances() {
        return bandwidthTolerances;
    }

    public String getLogID() {
        return this.cliArgs.getString("logId");
    }

    public BGPMaster getBgpMaster() {
        return bgpMaster;
    }

    public EvalSim getEvalSim() {
        return evalSim;
    }

    public TIntObjectMap<AS> getActiveTopology() {
        return this.activeTopology;
    }

    public TIntObjectMap<AS> getPrunedTopology() {
        return prunedTopology;
    }

    public MongoClient getMongoClient() {
        return mongoClient;
    }

    public MongoDatabase getMongoDatabase() {
        return mongoDatabase;
    }

    public Config getConfig() {
        return config;
    }
}
