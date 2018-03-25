package chaos.eval.nyx.src;

import chaos.logging.ThreadedWriter;
import chaos.sim.*;
import chaos.topo.AS;
import chaos.topo.BGPPath;
import chaos.topo.BotParser;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.client.MongoCollection;
import gnu.trove.map.TIntObjectMap;
import org.bson.Document;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;

public class ReactiveEngine {


    private Chaos sim;
    private final ReactiveSerializationMaster reactiveSerializer;
    private TIntObjectMap<AS> prunedTopology;
    private TIntObjectMap<AS> activeTopology;
    private Set<Integer> botSet;
    private HashMap<Integer, Integer> botSetToBotIPs;
    private Set<Double> botThresholds;
    private Set<Double> bandwidthTolerances;
    private Set<Double> congestionFactors;
    private MongoCollection<Document> experimentResultsCollection;
    private MongoCollection<Document> disturbanceResultsCollection;
    private MongoCollection<Document> diversityResultsCollection;
    private Writer generalOut;
    private Writer disturbanceOut;
    private Writer experimentOut;
    private Writer diversityOut;
    private Writer transitOut;
    private Writer runDiversityOut;
    private PerformanceLogger perfLogger;
    private PerformanceLogger runPerfLogger;
    private PerformanceLogger pairPerfLogger;
    private ReactiveSerializationMaster serializer;
    private TrafficManager trafficManager;

    public ReactiveEngine(Chaos sim, TIntObjectMap<AS> activeMap, TIntObjectMap<AS> prunedMap,
                          String loggingDir, PerformanceLogger perfLogger,
                          Set<Double> bandwidthTolerances, TrafficManager trafficManager) {
        this.sim = sim;
        this.prunedTopology = prunedMap;
        this.activeTopology = activeMap;
        this.bandwidthTolerances = bandwidthTolerances;
        this.trafficManager = trafficManager;
        this.generalOut = this.sim.getGeneralOut();
        this.runPerfLogger = new PerformanceLogger(this.sim.getLogDir(), "run_perf.log");
        this.pairPerfLogger = new PerformanceLogger(this.sim.getLogDir(), "pair_perf.log");
        this.reactiveSerializer = new ReactiveSerializationMaster(this.sim.evalSim);
        boolean parallelLogging = this.sim.getParallelLogging();

        String mongoOutputPath = this.sim.getLogDir().replace("/", "").replace("-", "_");

        try {
            if (parallelLogging) {
                this.experimentOut = new ThreadedWriter(loggingDir + "experiment.log");
                this.transitOut = new ThreadedWriter(loggingDir + "transit.log");
                this.disturbanceOut = new ThreadedWriter(loggingDir + "disturbance.log");
                this.diversityOut = new ThreadedWriter(loggingDir + "diversity.log");
                this.runDiversityOut = new ThreadedWriter(loggingDir + "roundDiversityOut.log");

                Thread transitOutThread = new Thread((ThreadedWriter) this.transitOut, "Transit Output Thread");
                Thread experimentOutThread = new Thread((ThreadedWriter) this.experimentOut, "Experiment Output Thread");
                Thread disturbanceOutThread = new Thread((ThreadedWriter) this.disturbanceOut, "Disturbance Output Thread");

                transitOutThread.start();
                experimentOutThread.start();
                disturbanceOutThread.start();
            } else {
                this.experimentOut = new BufferedWriter(new FileWriter(loggingDir + "experiment.log"));
                this.disturbanceOut = new BufferedWriter(new FileWriter(loggingDir + "disturbance.log"));
                this.transitOut = new BufferedWriter(new FileWriter(loggingDir + "transit.log"));
                this.diversityOut = new BufferedWriter(new FileWriter(loggingDir + "diversity.log"));
                this.runDiversityOut = new BufferedWriter(new FileWriter(loggingDir + "runDiversityOut.log"));
            }
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
        this.perfLogger = perfLogger;
        this.serializer = serializer;

        /*
         * Build set of bot ASNs.
         */
        this.logMessage("Building bot related information...");
        BotInfo botInfo = BotParser.parseBotASNFile(this.sim);
        this.botSet = botInfo.getBotSet();
        this.botSetToBotIPs = botInfo.getBotSetToBotIPs();
        this.setBotASes(this.activeTopology);
        this.setBotASes(this.prunedTopology);
        this.logMessage("Built bot related information.");

        this.botThresholds = this.sim.getConfig().botThresholds;
        this.congestionFactors = this.sim.getConfig().congestionFactors;

        if (this.sim.getConfig().preAttackSFDataCollection) {
            System.out.println("Collecting pre-attack subscription factor data and saving it to mongo...");

            MongoCollection<Document> PreAttackSFDataActive = this.sim.mongoDatabase.getCollection("PreAttackSFDataActive");

            // Delete existing data
            PreAttackSFDataActive.deleteMany(new Document());

            List<Document> bandwidthToleranceDocumentsActive = new ArrayList<>();
            for (Double bandwidthTolerance : this.sim.getBandwidthTolerances()) {
                List<Document> ases = new ArrayList<>();
                for (AS activeAS : this.activeTopology.valueCollection()) {

                    List<Document> neighbors = new ArrayList<>();
                    for (Integer activeASNeighborASN : activeAS.getNeighbors()) {
                        Double originalTraffic = activeAS.getTrafficOverLinkBetween(activeASNeighborASN);
                        Double linkCapacity = activeAS.getLinkCapacityOverLinkBetween(activeASNeighborASN, bandwidthTolerance);
                        Double preSF = originalTraffic / linkCapacity;
                        neighbors.add(new Document("ASN", activeASNeighborASN).append("PreAttackSF", preSF));
                    }

                    ases.add(new Document("ASN", activeAS.getASN())
                            .append("BandwidthTolerance", bandwidthTolerance)
                            .append("Neighbors", neighbors));
                }
                bandwidthToleranceDocumentsActive.add(new Document("BandwidthTolerance", bandwidthTolerance).append("ASes", ases));
            }
            PreAttackSFDataActive.insertMany(bandwidthToleranceDocumentsActive);

            MongoCollection<Document> PreAttackSFDataPruned = this.sim.mongoDatabase.getCollection("PreAttackSFDataPruned");

            // Delete existing data
            PreAttackSFDataPruned.deleteMany(new Document());

            List<Document> bandwidthToleranceDocumentsPruned = new ArrayList<>();
            for (Double bandwidthTolerance : this.sim.getBandwidthTolerances()) {
                List<Document> ases = new ArrayList<>();
                for (AS prunedAS : this.prunedTopology.valueCollection()) {

                    List<Document> neighbors = new ArrayList<>();
                    for (Integer prunedASNeighborASN : prunedAS.getNeighbors()) {
                        Double originalTraffic = prunedAS.getTrafficOverLinkBetween(prunedASNeighborASN);
                        Double linkCapacity = prunedAS.getLinkCapacityOverLinkBetween(prunedASNeighborASN, bandwidthTolerance);
                        Double preSF = originalTraffic / linkCapacity;
                        neighbors.add(new Document("ASN", prunedASNeighborASN).append("PreAttackSF", preSF));
                    }

                    ases.add(new Document("ASN", prunedAS.getASN())
                            .append("BandwidthTolerance", bandwidthTolerance)
                            .append("Neighbors", neighbors));
                }
                bandwidthToleranceDocumentsPruned.add(new Document("BandwidthTolerance", bandwidthTolerance).append("ASes", ases));
            }
            PreAttackSFDataPruned.insertMany(bandwidthToleranceDocumentsPruned);

            this.logMessage("Finished collecting pre-attack SF data. Exiting now!");
            System.exit(0);
        }

    }

    private List<Integer> getProvidersList(Set<AS> providers) {
        List<Integer> pList = new ArrayList<Integer>();
        for (AS tAS : providers) {
            pList.add(tAS.getASN());
        }
        return pList;
    }

    public void manageReactiveFullTest(int numRuns, boolean withBots, boolean disturbance, boolean skipLogging,
                                       boolean restarted, boolean onePathLimit, int restartNumRun,
                                       boolean targetdeployer) {
        /*
         * Object we will use to save bookmarking info for this run.
         */
        BookmarkInfo currentBookmarkingInfo = new BookmarkInfo();
        BookmarkInfo lastBookmark = new BookmarkInfo();

        Boolean ignoreOnePathLimit = onePathLimit;

        int startingRunNum = 0;

        if (restarted) {
            /*
             * Populate lastBookmark with the last set of bookmarking information if the simulation had to restart.
             */
            this.serializer.loadExpFromFile(lastBookmark);

            if (restartNumRun != 0) {
                startingRunNum = restartNumRun;
            } else {
                startingRunNum = lastBookmark.getRunNum();
            }

            this.logMessage(String.format("Restarting Mid-Level Experiment at runNum %d out of %d total runs",
                    startingRunNum, numRuns));
        } else {
            this.logMessage(String.format("Starting Mid-Level Experiment for %d", numRuns));
        }

        Set<Set<Integer>> usedASs;

        if (restarted) {
            usedASs = lastBookmark.getUsedASs();
        } else {
            usedASs = new HashSet<>();
        }

        for (int runNum = startingRunNum; runNum < numRuns; runNum += 1) {

            this.runPerfLogger.resetTimer();

            this.logMessage(String.format("\n----------------------------NEW RUN %d----------------------------\n", runNum));

            AS deployerAS = null;
            AS criticalAS = null;

            this.logMessage(String.format("Starting reactive mid level test run num: %d", runNum));

            if (this.sim.getConfig().runWithFixedDeployerCriticalPair) {
                deployerAS = this.activeTopology.get((Integer) this.sim.getConfig().fixedDeployerCriticalPair.toArray()[0]);
                criticalAS = this.activeTopology.get((Integer) this.sim.getConfig().fixedDeployerCriticalPair.toArray()[1]);
            } else {
                while (true) {
                    deployerAS = this.activeTopology.get(this.getRandomASN(this.activeTopology));
                    criticalAS = this.activeTopology.get(this.getRandomASN(this.activeTopology));

                    int numPathsBetweenDeployingAndCritical = deployerAS.getAllPathsTo(criticalAS.getASN()).size();

                    // Deal with one or less paths between deployerAS and criticalAS
                    if (!(ignoreOnePathLimit)) {
                        if (numPathsBetweenDeployingAndCritical <= 1) {
                            if (numPathsBetweenDeployingAndCritical == 1) {
                                this.logMessage("Could only find one path from Deploying to critical.");
                            } else {
                                this.logMessage("Could not find a path from Deploying to critical.");
                            }

                            this.logMessage("Trying another pair...");

                            continue;
                        }
                    }

                    if (withBots) {
                        if (this.sim.getConfig().runWithFullyDistributedBots) {
                            this.botSet.remove(deployerAS.getASN());
                            this.botSet.remove(criticalAS.getASN());
                        } else {
                            if (this.botSet.contains(deployerAS.getASN())) {
                                this.logMessage("Botset contains Deploying AS. Trying another pair...");
                                continue;
                            }
                        }
                    }

                    Set<Integer> thisPair = new HashSet<>();
                    thisPair.add(deployerAS.getASN());
                    thisPair.add(criticalAS.getASN());

                    this.logMessage(String.format("Trying pair: %d,%d", deployerAS.getASN(), criticalAS.getASN()));

                    if (!(usedASs.contains(thisPair))) {
                        usedASs.add(thisPair);
                        break;
                    } else {
                        this.logMessage(String.format("Pair %d,%d already exists in usedASs, trying another pair...",
                                deployerAS.getASN(), criticalAS.getASN()));
                    }
                }
            }

            this.logMessage(String.format("Selected deployerAS: %d", deployerAS.getASN()));
            this.logMessage(String.format("Selected criticalAS: %d", criticalAS.getASN()));

            DisturbanceRunner disturbanceRunner = null;
            if (disturbance) {
                disturbanceRunner = new DisturbanceRunner(this.sim, deployerAS.getASN());

                this.logMessage("Building disturbance maps used for collecting disturbance data...");

                disturbanceRunner.buildOriginalMaps(true);
                this.logMessage("Finished building the maps.");
            }

            try {
                runReactiveRun(deployerAS, criticalAS, runNum, withBots, disturbanceRunner, skipLogging, targetdeployer);
            } catch (OutOfMemoryError e) {
                this.sim.getMongoClient().close();
                System.exit(1);
            }

            this.logMessage(String.format("Done with reactive mid level test run num: %d", runNum));
            this.logMessage("Saving bookmark...");

            try (Writer writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream("runNum.txt"), "utf-8"))) {
                writer.write(runNum + "\n");
                writer.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

            currentBookmarkingInfo.setRunNum(runNum);
            currentBookmarkingInfo.setUsedASs(usedASs);
            this.serializer.serializeExpToFile(currentBookmarkingInfo);

            this.logMessage("Saved bookmark.");

            /*
             * Make sure to write out any logging in case we die and can't clean up.
             */
            try {
                this.generalOut.flush();
                this.experimentOut.flush();
                this.disturbanceOut.flush();
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(1);
            }

            this.runPerfLogger.logTime(String.format("Reactive Run %d", runNum), true);


            if (this.sim.getConfig().runWithFullyDistributedBots) {
                this.botSet.add(deployerAS.getASN());
                this.botSet.add(criticalAS.getASN());
            }

        }

    }

    private void runReactiveRun(AS deployerAS, AS criticalAS, Integer runNum, boolean withBots,
                                DisturbanceRunner disturbanceRunner, boolean skipLogging, boolean targetdeployer) {

        Integer DeployingSideASN;
        int originalPathLength;
        int nextHopOut;
        int criticalSideASN;

        System.out.println(String.format("Critical AS %d and Deploying AS %d", criticalAS.getASN(), deployerAS.getASN()));
        BGPPath originalPathFromCriticalToDeploying = criticalAS.getPath(deployerAS.getASN());

        this.logMessage(String.format("Original Path from Critical to Deploying: %s", originalPathFromCriticalToDeploying.toString()));
        originalPathLength = originalPathFromCriticalToDeploying.getPathLength();
        this.logMessage(String.format("Original Path Length from Critical to Deploying: %d", originalPathLength));

        this.logMessage("Running Volatile traffic computation...");
        this.resetTrafficOverAll();
        this.trafficManager.runVolatileTrafficComputation(deployerAS, TrafficManager.TrafficClassification.VOLATILE);
        this.logMessage("Done running Volatile traffic computation.");

        this.logMessage("Iterating over ASs between critical and Deploying...");

        this.logMessage(String.format("\n----------------------------NEW PAIR %d----------------------------\n", 0));

        this.logMessage("Running scenarios for very first pair (from the critical AS to the next hop)...");
        this.logMessage(String.format("Current CriticalSideASN: %d", criticalAS.getASN()));
        this.logMessage(String.format("Current DeployingSideASN: %d", originalPathFromCriticalToDeploying.getPath().get(0)));

        this.runScenarios(originalPathFromCriticalToDeploying.getNextHop(), criticalAS.getASN(), deployerAS, criticalAS, withBots,
                originalPathLength, runNum, 0, originalPathFromCriticalToDeploying, disturbanceRunner, targetdeployer);


        this.logMessage("Running scenarios for the pairs after the first...");
        for (int pairNum = 0; pairNum < (originalPathLength); pairNum += 1) {
            this.pairPerfLogger.resetTimer();
            this.logMessage(String.format("\n----------------------------NEW PAIR %d----------------------------\n", pairNum + 1));

            // this.logMessage(String.format("On iteration %d of lhs", pairNum));

            criticalSideASN = originalPathFromCriticalToDeploying.getPath().get(pairNum);
            this.logMessage(String.format("Current CriticalSideASN: %d", criticalSideASN));

            if (criticalSideASN == deployerAS.getASN()) {
                this.logMessage("DeployingAS is the same as the CriticalSideASN. Stopping.");
                break;
            }

            DeployingSideASN = originalPathFromCriticalToDeploying.getValidHopNum(criticalSideASN, 1);
            if (DeployingSideASN == null) {
                this.logMessage(String.format("Could not get path that's 1 hop out from the criticalSideASN %d. Stopping.", criticalSideASN));
                break;
            }
            this.logMessage(String.format("Current DeployingSideASN: %d", DeployingSideASN));


            this.runScenarios(DeployingSideASN, criticalSideASN, deployerAS, criticalAS, withBots,
                    originalPathLength, runNum, pairNum + 1, originalPathFromCriticalToDeploying, disturbanceRunner,
                    targetdeployer);
            this.logMessage(String.format("Finished testing criticalSideASN %d to DeployingSideASN %d", criticalSideASN, DeployingSideASN));
            this.pairPerfLogger.logTime(String.format("Reactive (Run %d, Pair %d)", runNum, pairNum), true);
        }

        this.logMessage("Resetting Traffic for new run...");
        this.resetTrafficForNewRun();
        this.logMessage("Done resetting Traffic for new run.");
    }

    private ModifiedPathTestInfo sendTestAdvertisement(Integer DeployingSideASN, Integer criticalSideASN, AS deployerAS, AS criticalAS, Set<Integer> lyingSet) {
        Set<Integer> emptyIntegerSet = new HashSet<Integer>();
        Set<AS> emptyASSet = new HashSet<AS>();
        Boolean withBots = false;
        Integer dummyScenarioNum = -1;
        AS nextHopOutAS = null;


        this.logMessage("\n----------------------------TEST ADVERTISEMENT----------------------------\n");

        this.logMessage("Sending out a test advertisement to gather the correct modified path to use for scenarios 1 and 2...");


        // If we have ASes to avoid in this test advertisement, insert them as the "ASesToLieAbout" set, which will be lied about.
        this.moveTrafficOffLinkWrapper(DeployingSideASN, criticalSideASN, deployerAS, withBots, lyingSet,
                emptyASSet, emptyIntegerSet, dummyScenarioNum);

        BGPPath pathFromCriticalToDeployer = criticalAS.getPath(deployerAS.getASN());
        nextHopOutAS = this.getNextHopOutFromdeployer(criticalAS, deployerAS, pathFromCriticalToDeployer);
        if (nextHopOutAS == null) {
            this.logMessage("Could not get hop directly out from deployer! Exiting...");
            System.exit(1);
        }

        this.logMessage("Resetting reverse poisoning on moved ASes...");
        deployerAS.resetReversePoison();
        this.logMessage("Done resetting reverse poisoning.");
        this.sim.getBgpMaster().driveBGPProcessing(this.activeTopology);

        this.logMessage("\n----------------------------END TEST ADVERTISEMENT----------------------------\n");


        return new ModifiedPathTestInfo(pathFromCriticalToDeployer, nextHopOutAS);
    }

    /**
     * Run all scenarios.
     *
     * Currently 3 Scenarios:
     *
     * 0: No disturbance mitigation
     * 1: Selective advertisement
     * 2: Selective advertisement and lining the path
     *
     * Run each of these scenarios with every combination of bandwidth tolerances and congestion factors.
     *
     * @param DeployingSideASN
     * @param criticalSideASN
     * @param deployerAS
     * @param criticalAS
     * @param withBots
     * @param originalPathLength
     * @param runNum
     * @param pairNum
     * @param originalPathFromCriticalToDeploying
     * @param disturbanceRunner
     * @throws IOException
     */
    private void runScenarios(Integer DeployingSideASN, Integer criticalSideASN, AS deployerAS, AS criticalAS,
                              Boolean withBots, Integer originalPathLength, Integer runNum, Integer pairNum,
                              BGPPath originalPathFromCriticalToDeploying, DisturbanceRunner disturbanceRunner,
                              boolean targetdeployer) {
        Set<AS> toAdvertisePoisoning = new HashSet<>();
        Set<Integer> ASesToLieAbout = new HashSet<>();
        HashMap<Integer, BotInfoContainer> validBotMap = null;

        if (targetdeployer) {
            validBotMap = this.getScenarioBotSetTargetingDeployer(DeployingSideASN, criticalSideASN, deployerAS,
                    criticalAS);
        } else {
            validBotMap = this.getScenarioBotSet(DeployingSideASN, criticalSideASN, deployerAS, criticalAS);
        }

        TreeMap<Integer, BotInfoContainer> botASIPs = new TreeMap<>();
        HashMap<Double, HashMap<Integer, BotInfoContainer>> thresholdBotMaps = new HashMap<>();
        Double totalIPs = 0.0;
        Double epsilon = this.sim.getConfig().botThresholdEpsilon;

        for (Map.Entry<Integer, BotInfoContainer> entry : validBotMap.entrySet()) {
            Integer thisBotASN = entry.getKey();
            BotInfoContainer botInfoContainer = entry.getValue();

            AS botAS = this.getAS(thisBotASN);

            if (botAS != null) {
                botInfoContainer.setNumBots(botAS.getNumBots());
                botASIPs.put(thisBotASN, botInfoContainer);
                totalIPs += botAS.getNumBots();
            }
        }

        this.logMessage(String.format("BotAS <-> IP Count Map Size: %d", botASIPs.size()));

        for (double botThreshold : this.botThresholds) {
            HashMap<Integer, BotInfoContainer> currentThresholdBotMap = new HashMap<>();
            this.logMessage(String.format("Current Target Threshold Value: %f", botThreshold));

            Double runningIPCount = 0.0;

            for (Map.Entry<Integer, BotInfoContainer> entry : botASIPs.entrySet()) {
                Integer thisBotASN = entry.getKey();
                BotInfoContainer botInfoContainer = entry.getValue();
                Integer numIPs = botInfoContainer.getNumBots();

                currentThresholdBotMap.put(thisBotASN, botInfoContainer);

                runningIPCount += numIPs;

                Double currentThreshold;
                currentThreshold = runningIPCount / totalIPs;

                if (almostEqual(currentThreshold, botThreshold, epsilon) || currentThreshold > botThreshold) {
                    this.logMessage(String.format("Current Accumulated Threshold %f and Current Target Threshold %f are within range or over.", currentThreshold, botThreshold));
                    break;
                }
            }

            this.logMessage(String.format("Size of thresholdBotSet for threshold %f: %d", botThreshold, currentThresholdBotMap.size()));
            thresholdBotMaps.put(botThreshold, currentThresholdBotMap);
        }

        for (Double congestionFactor : this.congestionFactors) {
            for (Double bandwidthTolerance : this.bandwidthTolerances) {
                for (double botThreshold : this.botThresholds) {
                    this.runScenario(0, DeployingSideASN, criticalSideASN, deployerAS, criticalAS, withBots, ASesToLieAbout, toAdvertisePoisoning,
                            originalPathLength, runNum, pairNum, originalPathFromCriticalToDeploying, disturbanceRunner, botThreshold, validBotMap, thresholdBotMaps, bandwidthTolerance,
                            congestionFactor, false, 0);
                }

                ModifiedPathTestInfo modifiedPathTestInfo = this.sendTestAdvertisement(DeployingSideASN, criticalSideASN, deployerAS, criticalAS, new HashSet<>());
                AS lastHopOnPath = this.activeTopology.get(modifiedPathTestInfo
                        .getLastHopOnPathFromCriticalToDeployer().getASN());
                this.logMessage(String.format("Last hop on path from critical to deployer that we will use for advertising is %d", lastHopOnPath.getASN()));
                toAdvertisePoisoning.add(lastHopOnPath);


                this.runScenario(1, DeployingSideASN, criticalSideASN, deployerAS, criticalAS, withBots, ASesToLieAbout, toAdvertisePoisoning,
                        originalPathLength, runNum, pairNum, originalPathFromCriticalToDeploying, disturbanceRunner, 0.0, validBotMap, thresholdBotMaps, bandwidthTolerance,
                        congestionFactor, false, 0);

                if (!(this.sim.getConfig().testAdvertisementOnly)) {
                    ASesToLieAbout = this.getTargetASes(modifiedPathTestInfo.getPathFromCriticalToDeployer(),
                            deployerAS, criticalAS);

                    if (this.sim.getConfig().debug) {
                        for (Integer asn : ASesToLieAbout) {
                            this.logMessage(String.format("AS to lie about: %d", asn));
                        }
                    }

                    ScenarioInfo scenarioInfo = this.runScenario(2, DeployingSideASN, criticalSideASN, deployerAS, criticalAS, false, ASesToLieAbout, toAdvertisePoisoning,
                            originalPathLength, runNum, pairNum, originalPathFromCriticalToDeploying, disturbanceRunner, 0.0, validBotMap, thresholdBotMaps,
                            bandwidthTolerance, congestionFactor, false, -1);

                    if (this.sim.getConfig().linkSearch) {
                        this.logMessage(String.format("\n----------------------------START LINK SEARCH----------------------------\n"));

                        if (!(scenarioInfo.getLargestModifiedSubscriptionFactor() <= 1.0)) {
                            this.logMessage("Largest modified subscription factor was not less than 1.0.");

                            if (scenarioInfo.getSuccess()) {

                                for (BGPPath path : deployerAS.getAllPathsTo(criticalAS.getASN())) {
                                    this.logMessage(String.format("Path from deployer to Critical: %s", path.toString()));
                                }

                                this.logMessage("Lying about the old critical side, which was unsuccessful.");
                                ASesToLieAbout.add(criticalSideASN);

                                if (this.sim.getConfig().linkSearchMaxIterations < 1) {
                                    this.logMessage("Link search max iterations constant is less than 1! That is not allowed. Exiting.");
                                    System.exit(0);
                                }

                                this.logMessage("Attempting congestion correction to achieve performance success via link searching...");

                                ScenarioInfo searchScenarioInfo = scenarioInfo;
                                int numIterations = 0;

                                while (numIterations < this.sim.getConfig().linkSearchMaxIterations) {
                                    this.logMessage(String.format("\n----------------------------LINK SEARCH ITERATION %d----------------------------\n", numIterations));
                                    toAdvertisePoisoning = new HashSet<>();
                                    ModifiedPathTestInfo searchTestAdvertisementInfo;
                                    Integer numBlacklisted = 0;

                                    this.logMessage("Blacklisting congested links from the critical to deployer if the original subscription factor is more than 1.0...");

                                    for (LinkInfo linkInfo : searchScenarioInfo.getModifiedCriticalToDeployingLinks()) {
                                        this.logMessage(String.format("Iterating over link %d -> %d with subscription factor %f", linkInfo.getCriticalSideASN(), linkInfo.getDeployingSideASN(), linkInfo.getSubscriptionFactor()));
                                        if (linkInfo.getCriticalSideASN() == deployerAS.getASN()) {
                                            this.logMessage(String.format("Current link Deploying side %d is the same as the deployer %d. Quitting blacklisting.", linkInfo.getDeployingSideASN(), deployerAS.getASN()));
                                            break;
                                        } else {
                                            if (linkInfo.getSubscriptionFactor() > 1.0 && linkInfo.getCriticalSideASN() != criticalAS.getASN()) {
                                                this.logMessage(String.format("Blacklisting link %d with subscription factor of %f", linkInfo.getCriticalSideASN(), linkInfo.getSubscriptionFactor()));
                                                ASesToLieAbout.add(linkInfo.getCriticalSideASN());
                                                numBlacklisted += 1;
                                            }
                                        }
                                    }
                                    this.logMessage(String.format("Blacklisted %d congested ASes.", numBlacklisted));

                                    this.logMessage("Resetting reverse poisoning on moved ASes...");
                                    deployerAS.resetReversePoison();
                                    this.logMessage("Done resetting reverse poisoning.");
                                    this.sim.getBgpMaster().driveBGPProcessing(this.activeTopology);

                                    searchTestAdvertisementInfo = this.sendTestAdvertisement(DeployingSideASN, criticalSideASN, deployerAS, criticalAS, ASesToLieAbout);
                                    toAdvertisePoisoning.add(searchTestAdvertisementInfo.getLastHopOnPathFromCriticalToDeployer());
                                    this.logMessage(String.format("Last hop on path from critical to deployer that we will use for advertising is %d", modifiedPathTestInfo.getLastHopOnPathFromCriticalToDeployer().getASN()));

                                    ASesToLieAbout.addAll(this.getTargetASes(searchTestAdvertisementInfo.getPathFromCriticalToDeployer(), deployerAS, criticalAS));

                                    if (this.sim.getConfig().debug) {
                                        this.logMessage(String.format("Outputting ASes we are lying about for iteration %d of searching...", numIterations));
                                        for (Integer asn : ASesToLieAbout) {
                                            this.logMessage(String.format("AS to lie about: %d", asn));
                                        }
                                    }

                                    searchScenarioInfo = this.runScenario(2, DeployingSideASN, criticalSideASN, deployerAS, criticalAS, false, ASesToLieAbout, toAdvertisePoisoning,
                                            originalPathLength, runNum, pairNum, originalPathFromCriticalToDeploying, disturbanceRunner, 0.0, validBotMap, thresholdBotMaps,
                                            bandwidthTolerance, congestionFactor, true, numIterations);

                                    if (!(searchScenarioInfo.getSuccess())) {
                                        this.logMessage("When searching, routing move was unsuccessful. Stopping now.");
                                        String testResult = String.format("RunNum-%d-PairNum-%d-SearchSuccess-2", runNum, pairNum);
                                        try {
                                            this.experimentOut.write(String.format("%s\n", testResult));
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;

                                    }

                                    if (searchScenarioInfo.getLargestModifiedSubscriptionFactor() <= 1.0) {
                                        this.logMessage("Found successful path! Stopping now.");
                                        String testResult = String.format("RunNum-%d-PairNum-%d-SearchSuccess-1", runNum, pairNum);
                                        try {
                                            this.experimentOut.write(String.format("%s\n", testResult));
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        }
                                        break;
                                    }

                                    this.logMessage(String.format("\n----------------------------END LINK SEARCH ITERATION %d----------------------------\n", numIterations));
                                    numIterations += 1;
                                }

                                if (numIterations == this.sim.getConfig().linkSearchMaxIterations) {
                                    String testResult = String.format("RunNum-%d-PairNum-%d-SearchSuccess-0", runNum, pairNum);
                                    try {
                                        this.experimentOut.write(String.format("%s\n", testResult));
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }
                        }
                        this.logMessage("Largest modified subscription factor was okay!");
                        this.logMessage("\n----------------------------END LINK SEARCH----------------------------\n");
                    }
                }
            }
        }
    }

    /**
     * Run a scenario.
     *
     * @param scenarioNum
     * @param DeployingSideASN
     * @param criticalSideASN
     * @param deployerAS
     * @param criticalAS
     * @param withBots
     * @param ASesToLieAbout
     * @param toAdvertisePoisoning
     * @param originalPathLength
     * @param runNum
     * @param pairNum
     * @param originalPathFromCriticalToDeploying
     * @param disturbanceRunner
     * @throws IOException
     */
    private ScenarioInfo runScenario(Integer scenarioNum, Integer DeployingSideASN, Integer criticalSideASN,
                                     AS deployerAS, AS criticalAS, Boolean withBots,
                                     Set<Integer> ASesToLieAbout, Set<AS> toAdvertisePoisoning,
                                     Integer originalPathLength, Integer runNum, Integer pairNum,
                                     BGPPath originalPathFromCriticalToDeploying,
                                     DisturbanceRunner disturbanceRunner,
                                     Double botThreshold, HashMap<Integer, BotInfoContainer> validBotMap,
                                     HashMap<Double, HashMap<Integer, BotInfoContainer>> thresholdBotSets,
                                     Double bandwidthTolerance, Double congestionFactor, Boolean rerun, Integer rerunNum) {

        if (rerun) {
            this.logMessage(String.format("----------------------------RERUN SCENARIO %d----------------------------\n", scenarioNum));
        } else {
            this.logMessage(String.format("\n----------------------------SCENARIO %d----------------------------\n", scenarioNum));
        }

        int modifiedPathLength;
        int numASesLiedAbout = 0;
        ScenarioInfo scenarioInfo = new ScenarioInfo();
        BGPPath modifiedPathFromCriticalToDeploying;
        Boolean isMoveWithoutBandwidthSuccess;
        Set<LinkInfo> originalCriticalToDeployingLinks = new HashSet<>();
        Set<LinkInfo> modifiedCriticalToDeployingLinks = new HashSet<>();
        Set<Integer> toMoveBotSet = null;
        Integer toMoveBotSetSize = 0;

        numASesLiedAbout += 1; // For the link we're avoiding. In this case the DeployingSideASN.

        if (scenarioNum == 0) {
            HashMap<Integer, BotInfoContainer> toMoveMap = thresholdBotSets.get(botThreshold);
            toMoveBotSet = toMoveMap.keySet();
            toMoveBotSetSize = toMoveBotSet.size();
            numASesLiedAbout += toMoveBotSet.size();
        } else if (scenarioNum == 2) {
            numASesLiedAbout += ASesToLieAbout.size();
        } else if (scenarioNum == 1) {
            numASesLiedAbout += validBotMap.size();
        } else {
            this.logMessage("Invalid Scenario Number! Exiting...");
            System.exit(-1);
        }


        Double trafficAddressedToCurrentLink = this.activeTopology.get(criticalSideASN).getTrafficOverLinkBetween(DeployingSideASN);
        Double criticalToDeployingLinkCapacity = this.activeTopology.get(criticalSideASN).getLinkCapacityOverLinkBetween(DeployingSideASN, bandwidthTolerance);

        Double botTrafficNeeded = 0.0;
        this.logMessage(String.format("Getting bot traffic needed to fill up this links to a congestion factor of %f with a bandwidth tolerance of %f...",
                congestionFactor, bandwidthTolerance));
        if (validBotMap.keySet().size() != 0) {
            botTrafficNeeded = this.getBotTrafficNeeded(trafficAddressedToCurrentLink,
                    DeployingSideASN, criticalSideASN, bandwidthTolerance, congestionFactor, criticalToDeployingLinkCapacity);
        } else {
            this.logMessage("Not getting bot traffic needed because valid botset size is 0.");
        }
        this.logMessage("Done getting the bot traffic needed.");

        this.logMessage(String.format("Getting the amount of traffic we need to divide up and assign to each Bot AS to propagate in our current botset of size %d...",
                validBotMap.size()));
        HashMap<Integer, Double> botASNeededTrafficValues = this.getBotASNeededTrafficValues(validBotMap.keySet(), botTrafficNeeded);
        this.logMessage("Done getting the assigned values to each Bot AS.");

        this.logMessage(String.format("Updating traffic over the links on the path from valid bot ASes to the Deploying side ASN %d...",
                DeployingSideASN));
        this.trafficManager.runBotTrafficComputation(validBotMap, botASNeededTrafficValues, TrafficManager.TrafficClassification.BOT, DeployingSideASN, criticalSideASN);
        this.logMessage("Finished updating the traffic over the links.");

        Double actualBotTrafficOverCriticalToDeploying = this.activeTopology.get(criticalSideASN).getBotTraffic(DeployingSideASN);
        if ((double) actualBotTrafficOverCriticalToDeploying != botTrafficNeeded) {
            this.logMessage(String.format("Bot Traffic Needed (%f) for link from AS %d to AS %d is not the same as the actual bot traffic over the link (%f)!",
                    botTrafficNeeded, criticalSideASN, DeployingSideASN, actualBotTrafficOverCriticalToDeploying));
        } else {
            this.logMessage("Bot traffic needed matches the actual current targeted link.");
        }

        this.logMessage("Calculating the original subscription factors for the links along this path...");
        this.calculateOriginalSubscriptionFactors(originalPathFromCriticalToDeploying, criticalSideASN, DeployingSideASN, criticalAS,
                originalCriticalToDeployingLinks, bandwidthTolerance);
        this.logMessage("Done calculating the original subscription factors.");

        if (this.sim.getConfig().blacklistCongested) {
            Integer numBlacklisted = 0;
            this.logMessage("Blacklisting congested links from the critical to deployer if the original subscription factor is more than 1.0...");
            for (LinkInfo linkInfo : originalCriticalToDeployingLinks) {
                if (linkInfo.getCriticalSideASN() == deployerAS.getASN()) {
                    break;
                } else {
                    if (linkInfo.getSubscriptionFactor() > 1.0 && linkInfo.getCriticalSideASN() != criticalAS.getASN()) {
                        ASesToLieAbout.add(linkInfo.getCriticalSideASN());
                        numBlacklisted += 1;
                    }
                }
            }
            this.logMessage(String.format("Blacklisted %d congested ASes.", numBlacklisted));
        }

        if (scenarioNum == 0) {
            this.moveTrafficOffLinkWrapper(DeployingSideASN, criticalSideASN, deployerAS, withBots, ASesToLieAbout,
                    toAdvertisePoisoning, toMoveBotSet, scenarioNum);
        } else {
            this.moveTrafficOffLinkWrapper(DeployingSideASN, criticalSideASN, deployerAS, withBots, ASesToLieAbout,
                    toAdvertisePoisoning, validBotMap.keySet(), scenarioNum);
        }

        modifiedPathFromCriticalToDeploying = criticalAS.getPath(deployerAS.getASN());
        modifiedPathLength = modifiedPathFromCriticalToDeploying.getRealPathLength();

        this.logMessage(String.format("Modified Path from Critical to Deploying: %s",
                modifiedPathFromCriticalToDeploying.toString()));
        this.logMessage(String.format("Modified Path Length from Critical to Deploying: %d", modifiedPathLength));

        this.logMessage("Running volatile traffic and bot traffic model recomputation...");
        this.resetTrafficForCurrentVolatile();
        this.trafficManager.runVolatileTrafficComputation(deployerAS, TrafficManager.TrafficClassification.CURRENT_VOLATILE);
        this.resetTrafficForBots();
        this.trafficManager.runBotTrafficComputation(validBotMap, botASNeededTrafficValues, TrafficManager.TrafficClassification.BOT, DeployingSideASN, criticalSideASN);
        actualBotTrafficOverCriticalToDeploying = this.activeTopology.get(criticalSideASN).getBotTraffic(DeployingSideASN);
        if ((double) actualBotTrafficOverCriticalToDeploying != botTrafficNeeded) {
            this.logMessage(String.format("Bot Traffic Needed (%f) for link from AS %d to AS %d is not the same as the actual bot traffic over the link (%f)!",
                    botTrafficNeeded, criticalSideASN, DeployingSideASN, actualBotTrafficOverCriticalToDeploying));
        } else {
            this.logMessage("Bot traffic needed matches the actual current targeted link.");
        }

        this.logMessage("Finished running volatile traffic model recomputation.");

        this.logMessage("Calculating modified subscription factors...");
        this.calculateModifiedSubscriptionFactors(modifiedPathFromCriticalToDeploying, criticalSideASN, DeployingSideASN, criticalAS,
                modifiedCriticalToDeployingLinks, bandwidthTolerance);
        this.logMessage("Finished calculating modified subscription factors.");

        this.logMessage("Checking move success and getting the largest subscription factors...");
        isMoveWithoutBandwidthSuccess = checkMoveSuccess(modifiedPathFromCriticalToDeploying, DeployingSideASN, criticalSideASN,
                deployerAS, criticalAS);
        scenarioInfo.setSuccess(isMoveWithoutBandwidthSuccess);
        Double largestOriginalSubscriptionFactor = this.getLargestSubscriptionFactor(originalCriticalToDeployingLinks, true);
        Double largestModifiedSubscriptionFactor = this.getLargestSubscriptionFactor(modifiedCriticalToDeployingLinks, false);
        scenarioInfo.setLargestModifiedSubscriptionFactor(largestModifiedSubscriptionFactor);
        this.logMessage("Finished checking move success and getting the largest subscription factors.");

        this.logMessage("Logging our success metrics for this scenario run.");
        logSuccess(isMoveWithoutBandwidthSuccess, modifiedPathFromCriticalToDeploying, runNum, pairNum, DeployingSideASN, criticalSideASN,
                originalPathLength, modifiedPathLength, originalPathFromCriticalToDeploying, botThreshold, scenarioNum,
                numASesLiedAbout, bandwidthTolerance, congestionFactor, originalCriticalToDeployingLinks,
                modifiedCriticalToDeployingLinks, largestOriginalSubscriptionFactor, largestModifiedSubscriptionFactor,
                validBotMap.size(), ASesToLieAbout.size(), toMoveBotSetSize, criticalToDeployingLinkCapacity, rerun, rerunNum);
        this.logMessage("Done logging success metrics.");

        if (disturbanceRunner != null) {
            this.logMessage(String.format("Collecting disturbance stats for pair num %d on run num %d...",
                    pairNum, runNum));
            disturbanceRunner.compareMapsToCurrentState(runNum, pairNum, DeployingSideASN, criticalSideASN,
                    false, this.disturbanceOut, this.getDisturbanceResultsCollection(),
                    scenarioNum, this.sim.getLogID());
            this.logMessage("Finished collecting disturbance stats.");
        }

        scenarioInfo.setOriginalCriticalToDeployingLinks(originalCriticalToDeployingLinks);
        scenarioInfo.setModifiedCriticalToDeployingLinks(modifiedCriticalToDeployingLinks);

        /*
         * Reset BGP routes on deployerAS.
         */
        this.logMessage("Resetting reverse poisoning on moved ASes...");
        deployerAS.resetReversePoison();
        this.logMessage("Done resetting reverse poisoning.");
        this.sim.getBgpMaster().driveBGPProcessing(this.activeTopology);
        this.logMessage("Resetting Traffic for new scenario...");
        this.resetTrafficForNewScenario();
        this.logMessage("Done resetting Traffic for new scenario.");

        this.logMessage(String.format("\n----------------------------END SCENARIO %d----------------------------\n", scenarioNum));
        return scenarioInfo;
    }

    private Double getLargestSubscriptionFactor(Set<LinkInfo> links, Boolean original) {
        Double maxFactor = 0.0;
        Integer i = 0;
        Integer DeployingSideASN = null;
        Integer criticalSideASN = null;
        for (LinkInfo linkInfo : links) {
            if (i == 0) {
                maxFactor = linkInfo.getSubscriptionFactor();
                DeployingSideASN = linkInfo.getDeployingSideASN();
                criticalSideASN = linkInfo.getCriticalSideASN();
            } else {
                if (maxFactor < linkInfo.getSubscriptionFactor()) {
                    maxFactor = linkInfo.getSubscriptionFactor();
                    DeployingSideASN = linkInfo.getDeployingSideASN();
                    criticalSideASN = linkInfo.getCriticalSideASN();
                }
            }
            i += 1;
        }

        if (original) {
            this.logMessage(String.format("Largest Original Subscription Factor was %f over AS %d to AS %d", maxFactor, criticalSideASN, DeployingSideASN));
        } else {
            this.logMessage(String.format("Largest Modified Subscription Factor was %f over AS %d to AS %d", maxFactor, criticalSideASN, DeployingSideASN));
        }

        return maxFactor;
    }

    private void calculateOriginalSubscriptionFactors(BGPPath originalPathFromCriticalToDeploying,
                                                      Integer criticalSideASN,
                                                      Integer DeployingSideASN,
                                                      AS criticalAS,
                                                      Set<LinkInfo> originalCriticalToDeployingLinks,
                                                      Double bandwidthTolerance) {

        Integer firstHopOnPath = originalPathFromCriticalToDeploying.getNextHop();
        Double trafficOverFirstLink = criticalAS.getTrafficOverLinkBetween(firstHopOnPath) -
                criticalAS.getVolTraffic(firstHopOnPath) + criticalAS.getBotTraffic(firstHopOnPath) +
                criticalAS.getCurrentVolTraffic(firstHopOnPath);

        this.logMessage(String.format("Calculating Original Subscription factors for AS %d to AS %d: current traffic(%f) - volatile traffic(%f) + bot traffic(%f) + current volatile traffic(%f)",
                criticalAS.getASN(), firstHopOnPath, criticalAS.getTrafficOverLinkBetween(firstHopOnPath), criticalAS.getVolTraffic(firstHopOnPath), criticalAS.getBotTraffic(firstHopOnPath), criticalAS.getCurrentVolTraffic(firstHopOnPath)));
        Double linkCapacity = criticalAS.getLinkCapacityOverLinkBetween(firstHopOnPath, bandwidthTolerance);
        this.logMessage(String.format("Link Capacity: %f", linkCapacity));
        Double originalSubscriptionFactor = trafficOverFirstLink / linkCapacity;
        this.logMessage(String.format("Original Subscription Factor: %f", originalSubscriptionFactor));

        LinkInfo firstLink = new LinkInfo(criticalAS.getASN(), firstHopOnPath);
        firstLink.setSubscriptionFactor(originalSubscriptionFactor);
        originalCriticalToDeployingLinks.add(firstLink);

        for (int j = 0; j < originalPathFromCriticalToDeploying.getRealPathLength() - 1; j += 1) {
            int currentASN = originalPathFromCriticalToDeploying.getPath().get(j);
            AS currentAS = this.getNormalAS(currentASN);

            if (currentAS == null) {
                break;
            }

            int nextHopASN = originalPathFromCriticalToDeploying.getPath().get(j + 1);
            Double originalTrafficOverLink = currentAS.getTrafficOverLinkBetween(nextHopASN) + currentAS.getBotTraffic(nextHopASN);
            this.logMessage(String.format("Calculating Original Subscription factors for AS %d to AS %d: current traffic(%f) + bot traffic(%f)", currentASN, nextHopASN, currentAS.getTrafficOverLinkBetween(nextHopASN), currentAS.getBotTraffic(nextHopASN)));
            linkCapacity = currentAS.getLinkCapacityOverLinkBetween(nextHopASN, bandwidthTolerance);
            this.logMessage(String.format("Link Capacity: %f", linkCapacity));
            originalSubscriptionFactor = originalTrafficOverLink / linkCapacity;
            this.logMessage(String.format("Original Subscription Factor: %f", originalSubscriptionFactor));

            LinkInfo thisLink = new LinkInfo(currentASN, nextHopASN);
            thisLink.setSubscriptionFactor(originalSubscriptionFactor);
            originalCriticalToDeployingLinks.add(thisLink);
        }
    }

    private void calculateModifiedSubscriptionFactors(BGPPath modifiedPathFromCriticalToDeploying,
                                                      Integer criticalSideASN,
                                                      Integer DeployingSideASN,
                                                      AS criticalAS,
                                                      Set<LinkInfo> modifiedCriticalToDeployingLinks,
                                                      Double bandwidthTolerance) {

        Integer firstHopOnPath = modifiedPathFromCriticalToDeploying.getNextHop();
        Double trafficOverFirstLink = criticalAS.getTrafficOverLinkBetween(firstHopOnPath) -
                criticalAS.getVolTraffic(firstHopOnPath) + criticalAS.getBotTraffic(firstHopOnPath) +
                criticalAS.getCurrentVolTraffic(firstHopOnPath);

        this.logMessage(String.format("Calculating Modified Subscription factors for AS %d to AS %d: current traffic(%f) - volatile traffic(%f) + bot traffic(%f) + current volatile traffic(%f)",
                criticalAS.getASN(), firstHopOnPath, criticalAS.getTrafficOverLinkBetween(firstHopOnPath), criticalAS.getVolTraffic(firstHopOnPath), criticalAS.getBotTraffic(firstHopOnPath), criticalAS.getCurrentVolTraffic(firstHopOnPath)));
        Double linkCapacity = criticalAS.getLinkCapacityOverLinkBetween(firstHopOnPath, bandwidthTolerance);
        this.logMessage(String.format("Link Capacity: %f", linkCapacity));
        Double modifiedSubscriptionFactor = trafficOverFirstLink / linkCapacity;
        this.logMessage(String.format("Modified Subscription Factor: %f", modifiedSubscriptionFactor));

        LinkInfo firstLink = new LinkInfo(criticalAS.getASN(), firstHopOnPath);
        firstLink.setSubscriptionFactor(modifiedSubscriptionFactor);
        modifiedCriticalToDeployingLinks.add(firstLink);

        for (int j = 0; j < modifiedPathFromCriticalToDeploying.getRealPathLength() - 1; j += 1) {
            int currentASN = modifiedPathFromCriticalToDeploying.getPath().get(j);
            AS currentAS = this.getNormalAS(currentASN);

            if (currentAS == null) {
                break;
            }

            int nextHopASN = modifiedPathFromCriticalToDeploying.getPath().get(j + 1);
            Double modifiedTrafficOverLink = currentAS.getTrafficOverLinkBetween(nextHopASN) -
                    currentAS.getVolTraffic(nextHopASN) + currentAS.getBotTraffic(nextHopASN) +
                    currentAS.getCurrentVolTraffic(nextHopASN);
            this.logMessage(String.format("Calculating Modified Subscription factors for AS %d to AS %d: current traffic(%f) - volatile traffic(%f) + bot traffic(%f) + current volatile traffic(%f)",
                    currentASN, nextHopASN, currentAS.getTrafficOverLinkBetween(nextHopASN), currentAS.getVolTraffic(nextHopASN), currentAS.getBotTraffic(nextHopASN), currentAS.getCurrentVolTraffic(nextHopASN)));
            linkCapacity = currentAS.getLinkCapacityOverLinkBetween(nextHopASN, bandwidthTolerance);
            this.logMessage(String.format("Link Capacity: %f", linkCapacity));
            modifiedSubscriptionFactor = modifiedTrafficOverLink / linkCapacity;
            this.logMessage(String.format("Modified Subscription Factor: %f", modifiedSubscriptionFactor));

            LinkInfo thisLink = new LinkInfo(currentASN, nextHopASN);
            thisLink.setSubscriptionFactor(modifiedSubscriptionFactor);
            modifiedCriticalToDeployingLinks.add(thisLink);
        }
    }

    private HashMap<Integer, Double> getBotASNeededTrafficValues(Set<Integer> botSet, Double botTrafficNeeded) {
        TreeMap<Integer, Integer> botASIPs = new TreeMap<>();
        HashMap<Integer, Double> botASNeededTrafficValues = new HashMap<>();
        Double totalIPs = 0.0;

        this.logMessage(String.format("Bot traffic needed: %f", botTrafficNeeded));

        for (int botASN : botSet) {
            AS botAS = this.getAS(botASN);

            if (botAS != null) {
                botASIPs.put(botASN, botAS.getNumBots());
                totalIPs += botAS.getNumBots();
            }
        }

        for (Map.Entry<Integer, Integer> entry : botASIPs.entrySet()) {
            Integer thisBotASN = entry.getKey();
            Integer thisBotASIPs = entry.getValue();
            Double thisBotsTraffic = (thisBotASIPs / totalIPs) * botTrafficNeeded;

            // this.logMessage(String.format("This Bot AS: %d, Bot Traffic to be sent: %f", thisBotASN, thisBotsTraffic));

            botASNeededTrafficValues.put(thisBotASN, thisBotsTraffic);
        }

        return botASNeededTrafficValues;
    }

    private Double getBotTrafficNeeded(Double trafficAddressedToCurrentLink, Integer DeployingSideASN,
                                       Integer criticalSideASN, Double bandwidthTolerance, Double congestionFactor,
                                       Double linkCapacity) {
        this.logMessage(String.format("Getting bot traffic needed: Link Capacity (%f) * Congestion factor (%f) -  traffic to current link (%f)", linkCapacity, congestionFactor, trafficAddressedToCurrentLink));
        return (linkCapacity * congestionFactor) - trafficAddressedToCurrentLink;
    }

    private HashMap<Integer, BotInfoContainer> getScenarioBotSet(Integer DeployingSideASN, Integer criticalSideASN, AS deployerAS, AS criticalAS) {

        ConcurrentHashMap<Integer, BotInfoContainer> criticalToDeployingValidBotMap = new ConcurrentHashMap<Integer, BotInfoContainer>();
        Integer numNullBotASes = 0;
        ExecutorService threadPool;
        final int WAIT_SECONDS = Integer.MAX_VALUE;

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ValidBotSetCollectorTask-%d")
                .setDaemon(true)
                .build();

        threadPool = new ThreadPoolExecutor(this.sim.getConfig().numThreads, this.sim.getConfig().numThreads, 0L, TimeUnit
                .MILLISECONDS,
                new TrafficManager.LimitedQueue<Runnable>(500), threadFactory);

        this.logMessage("Building total valid bot set...");

        System.out.println(String.format("Master Bot Set Size: %d", this.botSet.size()));
        for (int botASN : this.botSet) {
            if (botASN != deployerAS.getASN() && botASN != criticalAS.getASN()) {

                AS botAS = this.getAS(botASN);

                if (botAS == null) {
                    numNullBotASes++;
                    continue;
                }

                threadPool.submit(new BotCollectorTask(criticalToDeployingValidBotMap, this.activeTopology,
                        this.prunedTopology, (AS) botAS, DeployingSideASN, criticalSideASN));
            }
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            final List<Runnable> rejected = threadPool.shutdownNow();
            this.logMessage(String.format("Rejected tasks: %d", rejected.size()));
        }

        this.logMessage("Finished building total valid bot set.");
        this.logMessage(String.format("Total of %d bot ASes were not found in the active or pruned topologies", numNullBotASes));
        System.out.println(String.format("Size of validBotSet: %d", criticalToDeployingValidBotMap.size()));

        return new HashMap<>(criticalToDeployingValidBotMap);
    }

    private HashMap<Integer, BotInfoContainer> getScenarioBotSetTargetingDeployer(Integer DeployingSideASN, Integer
            criticalSideASN, AS deployerAS, AS criticalAS) {

        ConcurrentHashMap<Integer, BotInfoContainer> criticalToDeployingValidBotMap = new ConcurrentHashMap<Integer, BotInfoContainer>();
        Integer numNullBotASes = 0;
        ExecutorService threadPool;
        final int WAIT_SECONDS = Integer.MAX_VALUE;

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("ValidBotSetCollectorTask-%d")
                .setDaemon(true)
                .build();

        threadPool = new ThreadPoolExecutor(this.sim.getConfig().numThreads, this.sim.getConfig().numThreads, 0L, TimeUnit
                .MILLISECONDS,
                new TrafficManager.LimitedQueue<Runnable>(500), threadFactory);

        this.logMessage("Building total valid bot set...");

        System.out.println(String.format("Master Bot Set Size: %d", this.botSet.size()));
        for (int botASN : this.botSet) {
            if (botASN != deployerAS.getASN() && botASN != criticalAS.getASN()) {

                AS botAS = this.getAS(botASN);

                if (botAS == null) {
                    numNullBotASes++;
                    continue;
                }

                threadPool.submit(new BotTargetingDeployerCollectorTask(criticalToDeployingValidBotMap, this
                        .activeTopology,
                        this.prunedTopology, (AS) botAS, DeployingSideASN, criticalSideASN, deployerAS.getASN()));
            }
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            final List<Runnable> rejected = threadPool.shutdownNow();
            this.logMessage(String.format("Rejected tasks: %d", rejected.size()));
        }

        this.logMessage("Finished building total valid bot set.");
        this.logMessage(String.format("Total of %d bot ASes were not found in the active or pruned topologies", numNullBotASes));
        System.out.println(String.format("Size of validBotSet: %d", criticalToDeployingValidBotMap.size()));

        return new HashMap<>(criticalToDeployingValidBotMap);
    }


    /**
     * Get an AS from ASN, return null if not found.
     *
     * @param tASN
     * @return
     */
    private AS getAS(Integer tASN) {
        AS tAS = this.activeTopology.get(tASN);

        if (tAS == null) {
            tAS = this.prunedTopology.get(tASN);
        }

        return tAS;
    }

    /**
     * Get Normal AS from Bot ASN, return null if not found.
     *
     * @param botASN
     * @return
     */
    private AS getNormalAS(Integer botASN) {
        AS normalAS = this.activeTopology.get(botASN);

        if (normalAS == null) {
            normalAS = this.prunedTopology.get(botASN);
        }

        return normalAS;
    }

    /**
     * Get the first hop out from the deployer AS to the critical AS, which is the AS that we selectively advertise to
     * for technique 1 of disturbance mitigation.
     *
     * @param criticalAS
     * @param deployerAS
     */
    private AS getNextHopOutFromdeployer(AS criticalAS, AS deployerAS, BGPPath pathFromCriticalToDeployer) {
        Integer nextHopOut = null;
        AS nextHopOutAS = null;

        this.logMessage(String.format("Getting next hop out from deployer %d...", deployerAS.getASN()));
        for (int j = 0; j < pathFromCriticalToDeployer.getRealPathLength(); j += 1) {
            int currentASN = pathFromCriticalToDeployer.getPath().get(j);
            this.logMessage(String.format("Current ASN is %d", currentASN));

            if (currentASN == deployerAS.getASN()) {
                this.logMessage("Current ASN is the deployer AS!");
                try {
                    nextHopOut = pathFromCriticalToDeployer.getPath().get(j - 1);
                } catch (IndexOutOfBoundsException e) {
                    this.logMessage("Threw exception! Caught and moving on...");
                    BGPPath pathFromdeployerToCritical = deployerAS.getPath(criticalAS.getASN());
                    Integer nextAS = pathFromdeployerToCritical.getNextHop();
                    if (nextAS == null) {
                        nextHopOut = currentASN;
                    } else {
                        nextHopOut = nextAS;
                    }
                }
                break;
            }
        }

        this.logMessage(String.format("Chose next hop out from deployer as %d for the path from critical to deployer: %s", nextHopOut, pathFromCriticalToDeployer.toString()));

        nextHopOutAS = this.getNormalAS(nextHopOut);

        return nextHopOutAS;
    }

    /**
     * Check whether we were successful in moving traffic around.
     *
     * @param modifiedPathFromCriticalToDeploying
     * @param DeployingSideASN
     * @param deployerAS
     * @return
     */
    private Boolean checkMoveSuccess(BGPPath modifiedPathFromCriticalToDeploying, Integer DeployingSideASN, Integer criticalSideASN,
                                     AS deployerAS, AS criticalAS) {
        boolean isSuccess = false;

        if (criticalSideASN == criticalAS.getASN() && modifiedPathFromCriticalToDeploying.getNextHop() == DeployingSideASN) {
            this.logMessage("Modified path contained link from critical AS to next hop out.");
            isSuccess = false;
        } else if (!(modifiedPathFromCriticalToDeploying.containsLink(criticalSideASN, DeployingSideASN))) {
            this.logMessage(String.format("Modified path %s from the critical AS %d to the deployer AS %d did not contain the link from the critical side %d to the Deploying side %d",
                    modifiedPathFromCriticalToDeploying.toString(), criticalAS.getASN(), deployerAS.getASN(), criticalSideASN, DeployingSideASN));
            isSuccess = true;
        }

        return isSuccess;
    }

    /**
     * Log whether we are successful in moving traffic around.
     *
     * @param isSuccess
     * @param modifiedPathFromCriticalToDeploying
     * @param runNum
     * @param pairNum
     * @param DeployingSideASN
     * @param criticalSideASN
     * @param originalPathLength
     * @param modifiedPathLength
     * @param originalPathFromCriticalToDeploying
     * @throws IOException
     */
    private void logSuccess(Boolean isSuccess, BGPPath modifiedPathFromCriticalToDeploying, Integer runNum,
                            Integer pairNum, Integer DeployingSideASN, Integer criticalSideASN,
                            Integer originalPathLength, Integer modifiedPathLength,
                            BGPPath originalPathFromCriticalToDeploying, Double botThreshold, Integer scenarioNum,
                            Integer numASesLiedAbout, Double bandwidthTolerance, Double congestionFactor,
                            Set<LinkInfo> originalCriticalToDeployingLinks, Set<LinkInfo> modifiedCriticalToDeployingLinks,
                            Double largestOriginalSubscriptionFactor, Double largestModifiedSubscriptionFactor,
                            Integer validBotSetSize, Integer targetASSetSize, Integer toMoveBotSetSize,
                            Double criticalToDeployingLinkCapacity, Boolean rerun, Integer rerunNum) {

        int resultantPathLengthForCriticalAS;

        this.logMessage(String.format("Success: %s", isSuccess));

        resultantPathLengthForCriticalAS = modifiedPathFromCriticalToDeploying.getPathLength() - modifiedPathFromCriticalToDeploying.getRealPathLength();

        String testResult = String.format("ScenarioNum-%d-RunNum-%d-PairNum-%d-Success-%d-DeployingSideASN-%d-CriticalSideASN-%d-OriginalPathLen-%d-ModifiedPathLen-%d-ResultantPathLength-%s-BotThreshold-%s-NumASesLiedAbout-%s-BandwidthTolerance-%f-CongestionFactor-%f-LargestOriginalSubscriptionFactor-%f-LargestModifiedSubscriptionFactor-%f-ValidBotSetSize-%d-TargetBotSetSize-%d-ToMoveBotSetSize-%d-CriticalToDeployingLinkCapacity-%f-Rerun-%d-RerunNum-%d",
                scenarioNum, runNum, pairNum, (isSuccess) ? 1 : 0, DeployingSideASN,
                criticalSideASN, originalPathLength, modifiedPathLength,
                resultantPathLengthForCriticalAS, botThreshold, numASesLiedAbout,
                bandwidthTolerance, congestionFactor, largestOriginalSubscriptionFactor,
                largestModifiedSubscriptionFactor, validBotSetSize, targetASSetSize, toMoveBotSetSize,
                criticalToDeployingLinkCapacity, (rerun) ? 1 : 0, rerunNum);

        this.logMessage(String.format("Experiment Result: %s", testResult));

        Set<Double> originalCriticalToDeployingLinkSubscriptionFactors = new HashSet<>();
        Set<Double> modifiedCriticalToDeployingLinkSubscriptionFactors = new HashSet<>();

        for (LinkInfo linkInfo : originalCriticalToDeployingLinks) {
            originalCriticalToDeployingLinkSubscriptionFactors.add(linkInfo.getSubscriptionFactor());
        }

        for (LinkInfo linkInfo : modifiedCriticalToDeployingLinks) {
            modifiedCriticalToDeployingLinkSubscriptionFactors.add(linkInfo.getSubscriptionFactor());
        }

        try {
            this.experimentOut.write(String.format("%s\n", testResult));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Get ASes for lining the path to avoid disturbance in the topology.
     *
     * @param modifiedPathFromCriticalToDeploying
     * @param criticalAS
     * @return
     */
    private Set<Integer> getTargetASes(BGPPath modifiedPathFromCriticalToDeploying, AS deployerAS, AS criticalAS) {
        Set<Integer> ASesToLieAbout = new HashSet<>();

        this.logMessage(String.format("deployer AS: %d", deployerAS.getASN()));
        this.logMessage(String.format("Critical AS: %d", criticalAS.getASN()));
        this.logMessage(String.format("Path from Critical to deployer: %s", modifiedPathFromCriticalToDeploying.toString()));

        Set<Integer> asesOnPath = new HashSet<>();
        for (int j = 0; j < modifiedPathFromCriticalToDeploying.getRealPathLength(); j += 1) {
            int currentASN = modifiedPathFromCriticalToDeploying.getPath().get(j);
            asesOnPath.add(currentASN);
        }

        asesOnPath.add(criticalAS.getASN());
        asesOnPath.add(deployerAS.getASN());


        for (Integer asn : asesOnPath) {
            this.logMessage(String.format("AS on Path: %d", asn));
        }


        for (int j = 0; j < modifiedPathFromCriticalToDeploying.getRealPathLength(); j += 1) {
            int currentASN = modifiedPathFromCriticalToDeploying.getPath().get(j);
            AS currentAS = this.activeTopology.get(currentASN);

            if (currentASN == deployerAS.getASN()) {
                continue;
            }
            this.logMessage(String.format("Current ASN in getTargetASes: %d", currentASN));

            // This happens sometimes, I don't know why. If the AS is null, then just skip this step.
            if (currentAS == null) {
                break;
            }

            this.logMessage(String.format("CurrentASN active neighbors: %s", currentAS.getActiveNeighbors().toString()));
            for (Integer asn : currentAS.getActiveNeighbors()) {
                if (!(asesOnPath.contains(asn))) {
                    ASesToLieAbout.add(asn);
                }
            }
        }

        return ASesToLieAbout;
    }


    /**
     * Wrapper for the AS level function for moving traffic of of a link.
     *
     * @param DeployingSideASN
     * @param criticalSideASN
     * @param deployerAS
     * @param withBots
     * @param ASesToLieAbout
     * @param toAdvertisePoisoning
     */
    private void moveTrafficOffLinkWrapper(Integer DeployingSideASN, Integer criticalSideASN, AS deployerAS, Boolean withBots,
                                           Set<Integer> ASesToLieAbout, Set<AS> toAdvertisePoisoning, Set<Integer>
                                                   botSet, Integer scenarioNum) {
        String outputString;

        this.logMessage("\n----------------------------MOVING TRAFFIC----------------------------\n");


        this.logMessage(String.format("deployer AS is %d.", deployerAS.getASN()));
        this.logMessage(String.format("Deploying side ASN is %d, Critical side ASN is %d.", DeployingSideASN, criticalSideASN));
        if (this.sim.getConfig().debug) {
            this.logMessage(String.format("Lying about the following ASes: %s", ASesToLieAbout.toString()));
        }
        this.logMessage(String.format("Selectively advertising to the following ASes: %s", toAdvertisePoisoning.toString()));

        if (withBots && scenarioNum == 0) {
            this.logMessage("Moving traffic off of the link while also avoiding bot ASs");

            outputString = String.format("Botset size: %d", botSet.size());
            this.logMessage(outputString);

            deployerAS.setBotSet(botSet);
        }
        deployerAS.moveTrafficOffLink(criticalSideASN, DeployingSideASN, ASesToLieAbout, toAdvertisePoisoning, true);

        this.logMessage("\n----------------------------MOVING TRAFFIC----------------------------\n");

    }

    /**
     * Function for writing a string to both the general output log and the console.
     *
     * @param message
     */
    private void logMessage(String message) {
        System.out.println(message);
        try {
            this.generalOut.write(message + '\n');
        } catch (IOException e) {
            System.out.println(e.toString());
            System.exit(1);
        }
    }

    /**
     * Compare two numbers within eps of tolerance.
     *
     * @param a
     * @param b
     * @param eps
     * @return
     */
    private static boolean almostEqual(double a, double b, double eps) {
        return Math.abs(a - b) < eps;
    }

    /**
     * Randomly get a set of ASNs from a TIntObjectMap of ASs.
     *
     * @param asMap
     * @param numASs
     */
    private Set<Integer> getRandomASNSet(TIntObjectMap<AS> asMap, int numASs) {
        Set<Integer> retASs = new HashSet<>();

        int numAS = 0;
        while (numAS <= numASs) {
            int asn = getRandomASN(asMap);
            if (retASs.contains(asn)) {
                continue;
            }
            retASs.add(asn);
        }

        return retASs;
    }

    /**
     * Randomly get one ASN from a TIntObjectMap of ASs.
     *
     * @param asMap
     */
    private int getRandomASN(TIntObjectMap<AS> asMap) {
        Random generator = new Random();
        int[] keys = asMap.keys();

        return keys[generator.nextInt(keys.length)];
    }

    /**
     * Randomly get a set of ASNs from an Integer set of ASNs.
     *
     * @param asSet
     * @param numASs
     */
    private Set<Integer> getRandomASNSet(Set<Integer> asSet, int numASs) {
        Set<Integer> retASs = new HashSet<>();

        int numAS = 0;
        while (numAS <= numASs) {
            int asn = getRandomASN(asSet);
            if (retASs.contains(asn)) {
                continue;
            }
            retASs.add(asn);
        }

        return retASs;
    }

    /**
     * Randomly get one ASN from an Integer set of ASs.
     *
     * @param asSet
     */
    private int getRandomASN(Set<Integer> asSet) {
        Random generator = new Random();
        Object[] values = asSet.toArray();

        return (Integer) values[generator.nextInt(values.length)];
    }


    public void endSim() {
        try {
            this.generalOut.close();
            this.experimentOut.close();
            this.disturbanceOut.close();
            this.transitOut.close();
            this.diversityOut.close();
            this.runDiversityOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    private void resetTrafficForNewScenario() {
        for (AS thisAS : this.activeTopology.valueCollection()) {
            thisAS.resetScenarioTraffic();
        }

        for (AS thisAS : this.prunedTopology.valueCollection()) {
            thisAS.resetScenarioTraffic();
        }
    }

    private void resetTrafficForCurrentVolatile() {
        for (AS thisAS : this.activeTopology.valueCollection()) {
            thisAS.resetCurrentVolatileTraffic();
        }

        for (AS thisAS : this.prunedTopology.valueCollection()) {
            thisAS.resetCurrentVolatileTraffic();
        }
    }

    private void resetTrafficForBots() {
        for (AS thisAS : this.activeTopology.valueCollection()) {
            thisAS.resetBotTraffic();
        }

        for (AS thisAS : this.prunedTopology.valueCollection()) {
            thisAS.resetBotTraffic();
        }
    }

    private void resetTrafficOverAll() {
        for (AS thisAS : this.activeTopology.valueCollection()) {
            thisAS.resetTraffic();
        }

        for (AS thisAS : this.prunedTopology.valueCollection()) {
            thisAS.resetTraffic();
        }
    }

    private void resetTrafficForNewRun() {
        for (AS thisAS : this.activeTopology.valueCollection()) {
            thisAS.resetTraffic();
        }

        for (AS thisAS : this.prunedTopology.valueCollection()) {
            thisAS.resetTraffic();
        }
    }

    public MongoCollection<Document> getExperimentResultsCollection() {
        return experimentResultsCollection;
    }

    public MongoCollection<Document> getDisturbanceResultsCollection() {
        return disturbanceResultsCollection;
    }

    public Set<Integer> getBotSet() {
        return botSet;
    }

    public void setBotSet(Set<Integer> botSet) {
        this.botSet = botSet;
    }

    public Set<Integer> getbotSet() {
        return botSet;
    }

    public Set<Double> getBotThresholds() {
        return botThresholds;
    }

    public HashMap<Integer, Integer> getBotSetToBotIPs() {
        return botSetToBotIPs;
    }

    public void setBotSetToBotIPs(HashMap<Integer, Integer> botSetToBotIPs) {
        this.botSetToBotIPs = botSetToBotIPs;
    }

    private void setBotASes(TIntObjectMap<AS> topology) {
        for (AS thisAS : topology.valueCollection()) {
            if (this.getBotSet().contains(thisAS.getASN())) {
                thisAS.setBotAS(true);
                Integer numBots = this.getBotSetToBotIPs().get(thisAS.getASN());
                if (numBots == null) {
                    numBots = 1;
                }
                thisAS.setNumBots(numBots);
            }
        }
    }

    public MongoCollection<Document> getDiversityResultsCollection() {
        return diversityResultsCollection;
    }
}
