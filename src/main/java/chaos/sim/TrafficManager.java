package chaos.sim;

import chaos.eval.nyx.src.BotInfoContainer;
import chaos.topo.AS;
import chaos.topo.BGPPath;
import chaos.topo.SerializationMaster;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import gnu.trove.map.TIntObjectMap;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Based off of Bowen's ParallelTrafficStat, heavily revised to not be shitty.
 *
 * @author Jared M. Smith
 * @author Bowen (Original)
 */

public class TrafficManager {

    public enum TrafficClassification {
        VOLATILE, BOT, CURRENT_VOLATILE, NORMAL
    }

    private Chaos sim;

    private TIntObjectMap<AS> activeTopology;
    private TIntObjectMap<AS> purgedTopology;

    private Integer reactingSideASN;
    private Integer criticalSideASN;

    private final AtomicLong numUpdatedTrafficASes = new AtomicLong(0);
    private final AtomicLong numUpdatedLinkCapacityASes = new AtomicLong(0);
    private final AtomicLong numZeroTrafficFactors = new AtomicLong(0);
    private final AtomicLong numNullNormalActiveToActivePaths = new AtomicLong(0);
    private final AtomicLong numNullNormalActiveToPurgedPaths = new AtomicLong(0);
    private final AtomicLong numNullNormalPurgedToActivePaths = new AtomicLong(0);
    private final AtomicLong numNullNormalPurgedToPurgedPaths = new AtomicLong(0);
    private final AtomicLong numNullBotCombinedPaths = new AtomicLong(0);
    private final AtomicLong numBotToReactorPart1 = new AtomicLong(0);
    private final AtomicLong numBotToReactorPart2 = new AtomicLong(0);
    private final AtomicLong numBotToReactorPart3 = new AtomicLong(0);
    private final AtomicLong numBotToReactorPastPathNullCheck = new AtomicLong(0);
    private final AtomicLong numBotToReactorIteratedInWorker = new AtomicLong(0);
    private final AtomicLong getNumBotToReactorTimesBotAllTrafficCalled = new AtomicLong(0);
    private final AtomicLong numNullBotActiveToActivePaths = new AtomicLong(0);
    private final AtomicLong numNullBotActiveToPurgedPaths = new AtomicLong(0);
    private final AtomicLong numNullBotPurgedToActivePaths = new AtomicLong(0);
    private final AtomicLong numNullBotPurgedToPurgedPaths = new AtomicLong(0);
    private final AtomicLong numTimesBotWasDestASinBotTrafficToAll = new AtomicLong(0);
    private final AtomicLong numNullVolatileActiveToReactorPaths = new AtomicLong(0);
    private final AtomicLong numNullVolatilePurgedToReactorPaths = new AtomicLong(0);
    private final AtomicLong numTimesBotTrafficWentOverCriticalPath = new AtomicLong(0);
    private final AtomicLong numTimesBotDestASNull = new AtomicLong(0);
    private final AtomicLong numTimesBotASNull = new AtomicLong(0);


    private Set<Integer> trafficFactorCalulatedASes = ConcurrentHashMap.newKeySet();

    private static final boolean DEBUG = true;
    private static final boolean DEEP_DEBUG = false;
    private static final boolean REPORT_TIMING = true;

    private PerformanceLogger perfLog;

    /**
     * constructor function
     *
     * @param activeTopology
     * @param purgedTopology
     */
    public TrafficManager(Chaos sim, TIntObjectMap<AS> activeTopology, TIntObjectMap<AS> purgedTopology,
                          SerializationMaster serialControl, PerformanceLogger perfLogger) {

        this.activeTopology = activeTopology;
        this.purgedTopology = purgedTopology;
        this.perfLog = perfLogger;
        this.sim = sim;
    }

    /**
     * Run traffic flow over a set of ASes (localASList) for the active and pruned topologies.
     * <p>
     * This class is a runnable worker that gets ran through a thread.
     */
    private class NormalTrafficWorker implements Runnable {

        private List<Integer> localASList;
        private TrafficClassification trafficClassification;
        private Set<Double> bandwidthTolerances;

        NormalTrafficWorker(AS AS, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances) {
            this.localASList = new ArrayList<>();
            this.localASList.add(AS.getASN());
            this.trafficClassification = trafficClassification;
            this.bandwidthTolerances = bandwidthTolerances;
        }

        @Override
        public void run() {

            for (int tASN : this.localASList) {
                if (activeTopology.containsKey(tASN)) {
                    normalActiveToActive(activeTopology.get(tASN), this.trafficClassification, bandwidthTolerances, null);
                    normalActiveToPurged(activeTopology.get(tASN), this.trafficClassification, bandwidthTolerances, null);
                } else if (purgedTopology.containsKey(tASN)) {
                    HashMap<Integer, BGPPath> toActiveMap = normalPurgedToActive(purgedTopology.get(tASN), this.trafficClassification, bandwidthTolerances, null);
                    normalPurgedToPurged(purgedTopology.get(tASN), toActiveMap, this.trafficClassification, bandwidthTolerances, null);
                } else {
                    System.out.println("AS not in the topology. Exitting!");
                    System.exit(-1);
                }
            }
        }
    }

    private class VolatileTrafficWorker implements Runnable {

        private List<Integer> localASList;
        private TrafficClassification trafficClassification;
        private AS reactorAS;

        VolatileTrafficWorker(AS AS, AS reactorAS, TrafficClassification trafficClassification) {
            this.localASList = new ArrayList<>();
            this.localASList.add(AS.getASN());
            this.reactorAS = reactorAS;
            this.trafficClassification = trafficClassification;
        }

        @Override
        public void run() {

            for (int tASN : this.localASList) {
                if (activeTopology.containsKey(tASN)) {
                    volatileActiveToReactor(activeTopology.get(tASN), this.reactorAS, this.trafficClassification, null, null);
                } else if (purgedTopology.containsKey(tASN)) {
                    volatilePurgedToReactor(purgedTopology.get(tASN), this.reactorAS, this.trafficClassification, null, null);
                } else {
                    System.out.println("Reactor AS not in the Active topology. Exitting!");
                    System.exit(-1);
                }
            }
        }
    }


    /**
     * Run link capacity generation over a set of ASes (localASList) for the active and pruned topologies.
     * <p>
     * This class is a runnable worker that gets ran through a thread.
     */
    private class BotTrafficWorker implements Runnable {

        private AS botAS;
        private Double botTrafficNeededToSend;
        private TrafficClassification trafficClassification;
        private BotInfoContainer botInfoContainer;
        private TrafficManager tm;

        BotTrafficWorker(TrafficManager tm, AS botAS, BotInfoContainer botInfoContainer, TrafficClassification trafficClassification,
                         Double botTrafficNeededToSend) {
            this.tm = tm;
            this.botAS = botAS;
            this.botTrafficNeededToSend = botTrafficNeededToSend;
            this.trafficClassification = trafficClassification;
            this.botInfoContainer = botInfoContainer;
        }

        @Override
        public void run() {
            this.tm.numBotToReactorIteratedInWorker.incrementAndGet();
            botAllTraffic(botAS, botInfoContainer, trafficClassification, null, botTrafficNeededToSend);
        }
    }

    private void synchronousBotTrafficWorker(AS botAS, BotInfoContainer botInfoContainer, TrafficClassification trafficClassification,
                                             Double botTrafficNeededToSend) {
        this.numBotToReactorIteratedInWorker.incrementAndGet();
        botAllTraffic(botAS, botInfoContainer, trafficClassification, null, botTrafficNeededToSend);
    }

    /**
     * Run link capacity generation over a set of ASes (localASList) for the active and pruned topologies.
     * <p>
     * This class is a runnable worker that gets ran through a thread.
     */
    private class LinkCapacityWorker implements Runnable {

        private List<Integer> localASList;
        private Set<Double> bandwidthTolerances;

        LinkCapacityWorker(AS AS, Set<Double> bandwidthTolerances) {
            this.localASList = new ArrayList<>();
            this.localASList.add(AS.getASN());
            this.bandwidthTolerances = bandwidthTolerances;
        }

        @Override
        public void run() {

            for (int tASN : this.localASList) {
                if (activeTopology.containsKey(tASN)) {
                    normalActiveToActive(activeTopology.get(tASN), null, bandwidthTolerances, null);
                    normalActiveToPurged(activeTopology.get(tASN), null, bandwidthTolerances, null);
                } else if (purgedTopology.containsKey(tASN)) {
                    HashMap<Integer, BGPPath> toActiveMap = normalPurgedToActive(purgedTopology.get(tASN), null, bandwidthTolerances, null);
                    normalPurgedToPurged(purgedTopology.get(tASN), toActiveMap, null, bandwidthTolerances, null);
                } else {
                    System.out.println("AS not in the topology. Exitting!");
                    System.exit(-1);
                }
            }
        }
    }

    public void runLinkCapacityComputation(Set<Double> bandwidthTolerances) {
        this.perfLog.resetTimer();

        long startTime, thisTime;
        startTime = System.currentTimeMillis();

        /*
         * Run the traffic flow
         */
        this.computeLinkCapacities(bandwidthTolerances);
        thisTime = System.currentTimeMillis();
        if (TrafficManager.REPORT_TIMING) {
            this.sim.logMessage("Link capacity computation in network done, this took: "
                    + (thisTime - startTime) / 60000 + " minutes. ");
        }

        this.perfLog.logTime("Link capacity");

        if (TrafficManager.DEBUG) {
            testCapacities();
        }

    }

    public void runVolatileTrafficComputation(AS reactorAS, TrafficClassification trafficClassification) {

        ThreadPoolExecutor threadPool;
        final int WAIT_MILLISECONDS = Integer.MAX_VALUE;

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("VolatileTrafficParallelComputation-%d")
                .setDaemon(true)
                .build();

        threadPool = new ThreadPoolExecutor(this.sim.getConfig().numThreads, this.sim.getConfig().numThreads, 0L,
                TimeUnit
                .MILLISECONDS,
                new LimitedQueue<Runnable>(500), threadFactory);

        long startTime, thisTime, superAS;
        startTime = System.currentTimeMillis();

        for (AS activeAS : this.activeTopology.valueCollection()) {
            threadPool.submit(new VolatileTrafficWorker(activeAS, reactorAS, trafficClassification));
        }

        for (AS purgedAS : this.purgedTopology.valueCollection()) {
            threadPool.submit(new VolatileTrafficWorker(purgedAS, reactorAS, trafficClassification));
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(WAIT_MILLISECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            final List<Runnable> rejected = threadPool.shutdownNow();
            this.sim.logMessage(String.format("Rejected tasks: %d", rejected.size()));
        }

        thisTime = System.currentTimeMillis();
        if (TrafficManager.REPORT_TIMING) {
            this.sim.logMessage("Volatile traffic flowing in network done, this took: "
                    + (thisTime - startTime) / 60000 + " minutes. ");
        }

        this.perfLog.logTime("Volatile Traffic Flow");

        if (TrafficManager.DEBUG) {
            testTraffic(trafficClassification, null);
        }

    }

    public void runBotTrafficComputation(HashMap<Integer, BotInfoContainer> botASes, HashMap<Integer,
            Double> botASNeededTrafficValues,
                                         TrafficClassification trafficClassification, Integer reactorASN,
                                         Integer criticalASN) {

        Boolean parallel = true;
        this.reactingSideASN = reactorASN;
        this.criticalSideASN = criticalASN;
        ThreadPoolExecutor threadPool = null;
        final int WAIT_MILLISECONDS = Integer.MAX_VALUE;
        final ThreadFactory threadFactory;

        if (parallel) {
            threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("VolatileTrafficParallelComputation-%d")
                    .setDaemon(true)
                    .build();

            threadPool = new ThreadPoolExecutor(this.sim.getConfig().numThreads, this.sim.getConfig().numThreads, 0L, TimeUnit
                    .MILLISECONDS,
                    new LimitedQueue<Runnable>(500), threadFactory);
        }

        long startTime, thisTime, superAS;
        startTime = System.currentTimeMillis();

        int numBotASesInForLoop = 0;
        for (Map.Entry<Integer, BotInfoContainer> entry : botASes.entrySet()) {
            Integer botASN = entry.getKey();

            if (botASN == null) {
                if (TrafficManager.DEBUG) {
                    this.sim.logMessage("Bot ASN is null in runBotTrafficComputation()!");
                }
                continue;
            }

            if (this.activeTopology.get(botASN) == null && this.purgedTopology.get(botASN) == null) {
                if (TrafficManager.DEBUG) {
                    this.sim.logMessage("Bot AS is null in runBotTrafficComputation()!");
                }
                continue;
            }

            AS botAS = this.activeTopology.get(botASN);
            if (botAS == null) {
                botAS = this.purgedTopology.get(botASN);
                if (botAS == null) {
                    this.sim.logMessage("Bot AS is null in runBotTrafficComputation in for loop!");
                }
            }

            BotInfoContainer botInfoContainer = entry.getValue();
            Double botTrafficNeededToSend = botASNeededTrafficValues.get(botASN);

            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Sending bot traffic from %d with the bot traffic needed to be sent of: %f", botASN, botTrafficNeededToSend));
            }

            if (parallel) {
                threadPool.submit(new BotTrafficWorker(this, botAS, botInfoContainer, trafficClassification, botTrafficNeededToSend));
            } else {
                this.synchronousBotTrafficWorker(botAS, botInfoContainer, trafficClassification, botTrafficNeededToSend);
            }
            numBotASesInForLoop += 1;
        }

        this.sim.logMessage(String.format("Number of Bot ASes in For Loop of runBotTrafficComputation: %d", numBotASesInForLoop));

        if (parallel) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(WAIT_MILLISECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                final List<Runnable> rejected = threadPool.shutdownNow();
                this.sim.logMessage(String.format("Rejected tasks: %d", rejected.size()));
            }
        }

        thisTime = System.currentTimeMillis();
        if (TrafficManager.REPORT_TIMING) {
            this.sim.logMessage("Bot traffic flowing in network done, this took: "
                    + (thisTime - startTime) / 60000 + " minutes. ");
        }

        this.perfLog.logTime("Bot	 Traffic Flow");

        if (TrafficManager.DEBUG) {
            testTraffic(trafficClassification, botASes.keySet().size());
        }

    }

    public void runFullTrafficAndBandwidthComputation(Set<Double> bandwidthTolerances) {
        this.perfLog.resetTimer();

        long startTime, thisTime, superAS;
        startTime = System.currentTimeMillis();

        /*
         * Run the traffic flow
         */
        this.statTrafficInParallel(bandwidthTolerances);
        thisTime = System.currentTimeMillis();
        if (TrafficManager.REPORT_TIMING) {
            this.sim.logMessage("Full traffic flowing in network done, this took: "
                    + (thisTime - startTime) / 60000 + " minutes. ");
        }

        this.perfLog.logTime("Full Traffic Flow and Link Capacity Computation");

        if (TrafficManager.DEBUG) {
            testTraffic(TrafficClassification.NORMAL, null);
        }
    }

    public static class LimitedQueue<E> extends LinkedBlockingQueue<E> {
        public LimitedQueue(int maxSize) {
            super(maxSize);
        }

        @Override
        public boolean offer(E e) {
            // turn offer() and add() into a blocking calls (unless interrupted)
            try {
                put(e);
                return true;
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
            return false;
        }

    }

    public void statTrafficInParallel(Set<Double> bandwidthTolerances) {


        ThreadPoolExecutor threadPool;
        final Integer WAIT_MILLISECONDS = Integer.MAX_VALUE;

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("FullTrafficParallelComputation-%d")
                .setDaemon(true)
                .build();

        threadPool = new ThreadPoolExecutor(this.sim.getConfig().numThreads, this.sim.getConfig().numThreads, 0L,
                TimeUnit
                .MILLISECONDS,
                new LimitedQueue<Runnable>(500), threadFactory);

        for (AS tAS : this.activeTopology.valueCollection()) {
            threadPool.submit(new NormalTrafficWorker(tAS, TrafficClassification.NORMAL, bandwidthTolerances));
        }

        for (AS tAS : this.purgedTopology.valueCollection()) {
            threadPool.submit(new NormalTrafficWorker(tAS, TrafficClassification.NORMAL, bandwidthTolerances));
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            final List<Runnable> rejected = threadPool.shutdownNow();
            this.sim.logMessage(String.format("Rejected tasks: %d", rejected.size()));
        }
    }

    private void computeLinkCapacities(Set<Double> bandwidthTolerances) {

        ExecutorService threadPool;
        final Integer WAIT_MILLISECONDS = Integer.MAX_VALUE;

        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("LinkCapacityParallelWorker-%d")
                .setDaemon(true)
                .build();

        threadPool = new ThreadPoolExecutor(sim.getConfig().numThreads, sim.getConfig().numThreads, 0L, TimeUnit
                .MILLISECONDS,
                new LimitedQueue<Runnable>(500), threadFactory);

        for (AS tAS : this.activeTopology.valueCollection()) {
            threadPool.submit(new LinkCapacityWorker(tAS, bandwidthTolerances));
        }

        for (AS tAS : this.purgedTopology.valueCollection()) {
            threadPool.submit(new LinkCapacityWorker(tAS, bandwidthTolerances));
        }

        threadPool.shutdown();
        try {
            threadPool.awaitTermination(WAIT_MILLISECONDS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            final List<Runnable> rejected = threadPool.shutdownNow();
            this.sim.logMessage(String.format("Rejected tasks: %d", rejected.size()));
        }
    }

    private double calculateTrafficOnPath(AS srcAS, AS destAS) {
        double srcASTraffic = srcAS.getTrafficFactor();
        double destASTraffic = destAS.getTrafficFactor();

        if (TrafficManager.DEBUG) {
            if (this.trafficFactorCalulatedASes.contains(srcAS.getASN())) {
                if (srcASTraffic == 0.0) {
                    this.numZeroTrafficFactors.incrementAndGet();
                }
                this.trafficFactorCalulatedASes.add(srcAS.getASN());
            }

            if (this.trafficFactorCalulatedASes.contains(destAS.getASN())) {
                if (destASTraffic == 0.0) {
                    this.numZeroTrafficFactors.incrementAndGet();
                }
                this.trafficFactorCalulatedASes.add(destAS.getASN());
            }
        }

        return srcASTraffic * destASTraffic;
    }


    /**
     * for each current AS on the path, add its next hop as its neighbor with
     * the traffic amount equals to srcAS's ip count times destAS's ip count
     * <p>
     * parallel version
     *
     * @param thePath
     * @param srcAS
     * @param destAS
     * @return
     */
    private double addTrafficToPath(BGPPath thePath, AS srcAS, AS destAS,
                                    TrafficClassification trafficClassification,
                                    Set<Double> bandwidthTolerances,
                                    Double botTrafficNeededToSend) {

        double amountOfTraffic = this.calculateTrafficOnPath(srcAS, destAS);

        /*
         * Manually add traffic the source and the next hop on the path, as the
         * source will not appear on the path
         */
        if (trafficClassification != null) {
            if (trafficClassification == TrafficClassification.BOT) {
                srcAS.updateTrafficWithClassification(trafficClassification, thePath.getNextHop(), botTrafficNeededToSend);
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Adding %f bot traffic to the link between %d and %d which is the srcAS and the next hop", botTrafficNeededToSend, srcAS.getASN(), thePath.getNextHop()));
                }
            } else {
                srcAS.updateTrafficWithClassification(trafficClassification, thePath.getNextHop(), amountOfTraffic);
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Adding %f traffic to the link between %d and %d which is the srcAS and the next hop", amountOfTraffic, srcAS.getASN(), thePath.getNextHop()));
                }
            }
            this.numUpdatedTrafficASes.incrementAndGet();
        }

        if (bandwidthTolerances != null) {
            for (Double bandwidthTolerance : bandwidthTolerances) {
                srcAS.setLinkCapacityOverOneNeighbor(thePath.getNextHop(), bandwidthTolerance);
            }
            this.numUpdatedLinkCapacityASes.incrementAndGet();
        }

        /*
         * Add traffic for each of the remaining hops in the path
         */
        if (TrafficManager.DEEP_DEBUG) {
            this.sim.logMessage(String.format("Stepping along the path from %d to %d starting at next hop from source %d", srcAS.getASN(), destAS.getASN(), thePath.getNextHop()));
            this.sim.logMessage(String.format("Path is: %s", thePath.toString()));
            this.sim.logMessage(String.format("Real path length %d and path length %d", thePath.getRealPathLength(), thePath.getPathLength()));
        }


        for (int i = 0; i < (thePath.getRealPathLength() - 1); ++i) {

            AS currentAS = this.activeTopology.get(thePath.getPath().get(i));
            if (currentAS == null) {
                currentAS = this.purgedTopology.get(thePath.getPath().get(i));
            }
            AS nextAS = this.activeTopology.get(thePath.getPath().get(i + 1));
            if (nextAS == null) {
                nextAS = this.purgedTopology.get(thePath.getPath().get(i));
            }

            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Current AS is %d and next AS is %d", currentAS.getASN(), nextAS.getASN()));
            }
            /*
             * Placed here because we can have lying hole punched routes
             */
            if (currentAS.getASN() == thePath.getDestinationASN()) {
                break;
            }
            if (trafficClassification != null) {
                if (trafficClassification == TrafficClassification.BOT) {
                    currentAS.updateTrafficWithClassification(trafficClassification, nextAS.getASN(), botTrafficNeededToSend);
                    if (TrafficManager.DEEP_DEBUG) {
                        this.sim.logMessage(String.format("Adding %f bot traffic to the link between %d and %d", botTrafficNeededToSend, currentAS.getASN(), nextAS.getASN()));
                    }
                } else {
                    currentAS.updateTrafficWithClassification(trafficClassification, nextAS.getASN(), amountOfTraffic);
                    if (TrafficManager.DEEP_DEBUG) {
                        this.sim.logMessage(String.format("Adding %f traffic to the link between %d and %d", amountOfTraffic, currentAS.getASN(), nextAS.getASN()));
                    }
                }
                this.numUpdatedTrafficASes.incrementAndGet();
            }

            if (bandwidthTolerances != null) {
                for (Double bandwidthTolerance : bandwidthTolerances) {
                    currentAS.setLinkCapacityOverOneNeighbor(nextAS.getASN(), bandwidthTolerance);
                }
                this.numUpdatedLinkCapacityASes.incrementAndGet();
            }
        }

        return amountOfTraffic;
    }

    /**
     * does the same thing as addTrafficOnTheLinkBasisPath(BGPPath tpath,
     * AS srcAS, AS destAS), only difference is that the path doesn't
     * contain the AS on the purged map, so need to add them manually.
     * <p>
     * parallel version
     *
     * @param thePath
     * @param srcAS
     * @param destAS
     */
    private void addTrafficToPathAndLastHop(BGPPath thePath, AS srcAS, AS destAS,
                                            TrafficClassification trafficClassification,
                                            Set<Double> bandwidthTolerances, Double botTrafficNeededToSend) {

        double amountOfTraffic = this.addTrafficToPath(thePath, srcAS, destAS, trafficClassification,
                bandwidthTolerances, botTrafficNeededToSend);

        Integer realSize = thePath.getRealPathLength();
        AS lastASInRealPath = this.activeTopology.get(thePath.getPath().get(realSize - 1));
        if (lastASInRealPath == null) {
            lastASInRealPath = this.purgedTopology.get(thePath.getPath().get(realSize - 1));
        }

        if (trafficClassification != null) {
            if (trafficClassification == TrafficClassification.BOT) {
                lastASInRealPath.updateTrafficWithClassification(trafficClassification, destAS.getASN(), botTrafficNeededToSend);
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Adding %f bot traffic to the link between %d and %d", botTrafficNeededToSend, lastASInRealPath.getASN(), destAS.getASN()));
                }
            } else {
                lastASInRealPath.updateTrafficWithClassification(trafficClassification, destAS.getASN(), amountOfTraffic);
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Adding %f traffic to the link between %d and %d", amountOfTraffic, lastASInRealPath.getASN(), destAS.getASN()));
                }
            }
            this.numUpdatedTrafficASes.incrementAndGet();
        }

        if (bandwidthTolerances != null) {
            for (Double bandwidthTolerance : bandwidthTolerances) {
                lastASInRealPath.setLinkCapacityOverOneNeighbor(destAS.getASN(), bandwidthTolerance);
            }
            this.numUpdatedLinkCapacityASes.incrementAndGet();
        }
    }

    /**
     * Convert a set of ASes into a list of ASes
     *
     * @param providers ASes in a set
     * @return the same ASes only in a list
     */

    private List<Integer> getProvidersList(Set<AS> providers) {
        List<Integer> pList = new ArrayList<Integer>();
        for (AS tAS : providers) {
            pList.add(tAS.getASN());
        }
        return pList;
    }

    private void botAllTraffic(AS botAS, BotInfoContainer botInfoContainer, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                               Double botTrafficNeededToSend) {

        if (botAS == null) {
            this.numTimesBotASNull.incrementAndGet();
            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage("BotAS is null in botAllTraffic!");
            }
            return;
        }

        this.getNumBotToReactorTimesBotAllTrafficCalled.incrementAndGet();

        AS destAS = this.activeTopology.get(botInfoContainer.getDeployingSideASN());
        if (destAS == null) {
            if (TrafficManager.DEBUG) {
                this.sim.logMessage("Dest AS is null!");
            }
            this.numTimesBotDestASNull.incrementAndGet();
            return;
        }

        this.numBotToReactorPart1.incrementAndGet();

        BGPPath usedPath = botInfoContainer.getPathToDeployer();

        if (TrafficManager.DEEP_DEBUG) {
            this.sim.logMessage(String.format("Calculating traffic for %d to %d", botAS.getASN(), destAS.getASN()));
        }

        this.numBotToReactorPart2.incrementAndGet();

        if (botAS.getASN() == destAS.getASN()) {
            // this.sim.logMessage("Bot AS was dest AS!");
            this.numTimesBotWasDestASinBotTrafficToAll.incrementAndGet();
            return;
        }

        this.numBotToReactorPart3.incrementAndGet();

        /*
         * Fetch the actual path object, deal with the odd case in which it
         * does not exist
         */
        if (usedPath == null) {
            this.numNullBotCombinedPaths.incrementAndGet();
            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Bot active %d to active %d used path is null...", botAS.getASN(), destAS.getASN()));
            }
            return;
        }

        this.numBotToReactorPastPathNullCheck.incrementAndGet();

        if (usedPath.containsLink(this.criticalSideASN, this.reactingSideASN)) {
            this.numTimesBotTrafficWentOverCriticalPath.incrementAndGet();
            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Path from %d to %d actually goes over our target link.", botAS.getASN(), destAS.getASN()));
            }
        }

        if (TrafficManager.DEEP_DEBUG) {
            this.sim.logMessage(String.format("Actual path is: %s", usedPath.toString()));
        }

        this.addTrafficToPath(usedPath, botAS, destAS, trafficClassification, bandwidthTolerances, botTrafficNeededToSend);
    }

    private void volatileActiveToReactor(AS srcAS, AS reactorAS, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                                         Double botTrafficNeededToSend) {
        if (TrafficManager.DEEP_DEBUG) {
            this.sim.logMessage(String.format("Calculating traffic for %d to %d", reactorAS.getASN(), srcAS.getASN()));
        }
        if (srcAS.getASN() == reactorAS.getASN()) {
            return;
        }

        /*
         * Fetch the actual path object, deal with the odd case in which it
         * does not exist
         */
        BGPPath usedPath = srcAS.getPath(reactorAS.getASN());
        if (usedPath == null) {
            this.numNullVolatileActiveToReactorPaths.incrementAndGet();
            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Volatile active %d to active %d used path is null...", srcAS.getASN(), reactorAS.getASN()));
            }
            return;
        }

        /*
         * Actually add the traffic to the AS objects
         */
        this.addTrafficToPath(usedPath, srcAS, reactorAS, trafficClassification, bandwidthTolerances, botTrafficNeededToSend);
    }

    private void normalActiveToActive(AS srcAS, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                                      Double botTrafficNeededToSend) {

        Collection<AS> ASList = this.activeTopology.valueCollection();
        for (AS destAS : ASList) {

            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Calculating traffic for %d to %d", srcAS.getASN(), destAS.getASN()));
            }
            if (srcAS.getASN() == destAS.getASN()) {
                continue;
            }

            /*
             * Fetch the actual path object, deal with the odd case in which it
             * does not exist
             */
            BGPPath usedPath = srcAS.getPath(destAS.getASN());
            if (usedPath == null) {
                this.numNullNormalActiveToActivePaths.incrementAndGet();
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Normal active %d to active %d used path is null...", srcAS.getASN(), destAS.getASN()));
                }
                continue;
            }

            /*
             * Actually add the traffic to the AS objects
             */
            this.addTrafficToPath(usedPath, srcAS, destAS, trafficClassification, bandwidthTolerances, botTrafficNeededToSend);
        }
    }

    private void volatilePurgedToReactor(AS srcAS, AS reactorAS, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                                         Double botTrafficNeededToSend) {

        List<BGPPath> pathList = new ArrayList<>();

        /* get the path through the providers of nodes in purgedTopology */
        Set<AS> providers = srcAS.getProviders();

        int tdestActiveASN = reactorAS.getASN();
        pathList.clear();
        for (AS tProviderAS : providers) {
            BGPPath tpath = tProviderAS.getPath(tdestActiveASN);
            if (tpath == null) {
                continue;
            }
            BGPPath cpath = tpath.deepCopy();
            cpath.prependASToPath(tProviderAS.getASN());
            pathList.add(cpath);
        }

        BGPPath tpath = srcAS.pathSelection(pathList);

        if (tpath == null) {
            this.numNullVolatilePurgedToReactorPaths.incrementAndGet();
            if (TrafficManager.DEEP_DEBUG) {
                this.sim.logMessage(String.format("Volatile purged %d to active %d used path is null...", srcAS.getASN(), reactorAS.getASN()));
            }
            return;
        }

		/*
        if (srcAS.getProviders().contains(reactorAS)) {
            if (trafficClassification != null) {
                if (trafficClassification == TrafficClassification.BOT) {
                    srcAS.updateTrafficWithClassification(trafficClassification, reactorAS.getASN(), botTrafficNeededToSend);
                } else {
                    double amount = this.calculateTrafficOnPath(srcAS, reactorAS);
                    srcAS.updateTrafficWithClassification(trafficClassification, reactorAS.getASN(), amount);
                }
                this.numUpdatedTrafficASes.incrementAndGet();
            } else if (bandwidthTolerances != null){
                for (Double bandwidthTolerance : bandwidthTolerances) {
                    srcAS.setLinkCapacityOverOneNeighbor(reactorAS.getASN(), bandwidthTolerance);
                }
                this.numUpdatedLinkCapacityASes.incrementAndGet();
            }
        }*/
        this.addTrafficToPath(tpath, srcAS, reactorAS, trafficClassification,
                bandwidthTolerances, botTrafficNeededToSend);
    }

    private void normalActiveToPurged(AS srcActiveAS, TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                                      Double botTrafficNeededToSend) {


        Collection<AS> ASList = this.purgedTopology.valueCollection();
        for (AS tdestPurgedAS : ASList) {

            List<Integer> hookASNs = getProvidersList(tdestPurgedAS.getProviders());
            BGPPath tpath = srcActiveAS.getPathToPurged(hookASNs);
            if (tpath == null) {
                this.numNullNormalActiveToPurgedPaths.incrementAndGet();
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Normal active %d to purged %d used path is null...", srcActiveAS.getASN(), tdestPurgedAS.getASN()));
                }
                continue;
            }

            if (tdestPurgedAS.getProviders().contains(srcActiveAS)) {
                if (trafficClassification != null) {
                    if (trafficClassification == TrafficClassification.BOT) {
                        srcActiveAS.updateTrafficWithClassification(trafficClassification, tdestPurgedAS.getASN(), botTrafficNeededToSend);
                    } else {
                        double amount = 0.0;
                        amount = this.calculateTrafficOnPath(srcActiveAS, tdestPurgedAS);
                        srcActiveAS.updateTrafficWithClassification(trafficClassification, tdestPurgedAS.getASN(), amount);
                    }
                    this.numUpdatedTrafficASes.incrementAndGet();
                } else if (bandwidthTolerances != null) {
                    for (Double bandwidthTolerance : bandwidthTolerances) {
                        srcActiveAS.setLinkCapacityOverOneNeighbor(tdestPurgedAS.getASN(), bandwidthTolerance);
                    }
                    this.numUpdatedLinkCapacityASes.incrementAndGet();
                }
            }
            this.addTrafficToPathAndLastHop(tpath, srcActiveAS, tdestPurgedAS, trafficClassification,
                    bandwidthTolerances, botTrafficNeededToSend);
        }
    }


    private HashMap<Integer, BGPPath> normalPurgedToActive(AS srcPurgedAS, TrafficClassification trafficClassification,
                                                           Set<Double> bandwidthTolerances, Double botTrafficNeededToSend) {
        HashMap<Integer, BGPPath> bestPathMapping = new HashMap<>();
        List<BGPPath> pathList = new ArrayList<>();

        /* get the path through the providers of nodes in purgedTopology */
        Set<AS> providers = srcPurgedAS.getProviders();
        Collection<AS> ASList = this.activeTopology.valueCollection();
        for (AS tdestActiveAS : ASList) {

            int tdestActiveASN = tdestActiveAS.getASN();
            pathList.clear();
            for (AS tProviderAS : providers) {
                BGPPath tpath = tProviderAS.getPath(tdestActiveASN);
                if (tpath == null) {
                    continue;
                }
                BGPPath cpath = tpath.deepCopy();
                cpath.prependASToPath(tProviderAS.getASN());
                pathList.add(cpath);
            }

            BGPPath tpath = srcPurgedAS.pathSelection(pathList);
            if (tpath == null) {
                this.numNullNormalPurgedToActivePaths.incrementAndGet();
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Normal purged %d to active %d used path is null...", srcPurgedAS.getASN(), tdestActiveAS.getASN()));
                }
                continue;
            }

            AS destAS = this.activeTopology.get(tdestActiveASN);
            if (destAS == null) {
                destAS = this.activeTopology.get(tdestActiveASN);
            }

            this.addTrafficToPath(tpath, srcPurgedAS, destAS, trafficClassification, bandwidthTolerances, botTrafficNeededToSend);

            bestPathMapping.put(tdestActiveASN, tpath);
        }

        return bestPathMapping;
    }

    private void normalPurgedToPurged(AS srcPurgedAS, HashMap<Integer, BGPPath> toActiveMap,
                                      TrafficClassification trafficClassification, Set<Double> bandwidthTolerances,
                                      Double botTrafficNeededToSend) {

        List<BGPPath> pathList = new ArrayList<>();
        Collection<AS> ASList = this.purgedTopology.valueCollection();
        for (AS tdestPurgedAS : ASList) {

            int tdestPurgedASN = tdestPurgedAS.getASN();
            if (srcPurgedAS.getASN() == tdestPurgedASN) {
                continue;
            }

            pathList.clear();
            AS destAS = this.activeTopology.get(tdestPurgedASN);
            if (destAS == null) {
                destAS = this.purgedTopology.get(tdestPurgedASN);
            }
            List<Integer> destProviderList = this.getProvidersList(destAS.getProviders());
            for (int tDestHook : destProviderList) {
                if (toActiveMap.containsKey(tDestHook)) {
                    pathList.add(toActiveMap.get(tDestHook));
                }
            }

            BGPPath tpath = srcPurgedAS.pathSelection(pathList);
            if (tpath == null) {
                this.numNullNormalPurgedToPurgedPaths.incrementAndGet();
                if (TrafficManager.DEEP_DEBUG) {
                    this.sim.logMessage(String.format("Bot purged %d to purged %d used path is null...", srcPurgedAS.getASN(), tdestPurgedAS.getASN()));
                }
                continue;
            }

            destAS = this.activeTopology.get(tdestPurgedAS.getASN());
            if (destAS == null) {
                destAS = this.purgedTopology.get(tdestPurgedAS.getASN());
            }
            this.addTrafficToPathAndLastHop(tpath, srcPurgedAS, destAS, trafficClassification,
                    bandwidthTolerances, botTrafficNeededToSend);
        }
    }

    private void testCapacities() {
        Set<Double> bandwidthTolerances = this.sim.getBandwidthTolerances();

        this.sim.logMessage(String.format("Number of times the link capacities updated: %d", this.numUpdatedLinkCapacityASes.get()));

        Integer numberOneValueCapacities = 0;

        for (AS tAS : this.activeTopology.valueCollection()) {
            for (int tNeighborASN : tAS.getNeighbors()) {
                for (Double bandwidthTolerance : bandwidthTolerances) {
                    if (TrafficManager.DEEP_DEBUG) {
                        this.sim.logMessage("Active " + tAS.getASN() + ", " + tNeighborASN + ", Capacity for Tolerance " + Double.toString(bandwidthTolerance) + " - " + tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance));
                    }
                    if (tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance) == 1.0) {
                        numberOneValueCapacities += 1;
                    }
                }
                this.sim.logMessage("\n");
            }
        }

        this.sim.logMessage(String.format("Number of null link capacities in active: %d", numberOneValueCapacities));

        numberOneValueCapacities = 0;

        for (AS tAS : this.purgedTopology.valueCollection()) {
            for (int tNeighborASN : tAS.getNeighbors()) {
                for (Double bandwidthTolerance : bandwidthTolerances) {
                    if (TrafficManager.DEEP_DEBUG) {
                        this.sim.logMessage("Purged " + tAS.getASN() + ", " + tNeighborASN + ", Capacity " + tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance));
                    }
                    if (tAS.getLinkCapacityOverLinkBetween(tNeighborASN, bandwidthTolerance) == 1.0) {
                        numberOneValueCapacities += 1;
                    }
                }
                this.sim.logMessage("\n");
            }
        }

        this.sim.logMessage(String.format("Number of null link capacities in purged: %d", numberOneValueCapacities));
        this.numUpdatedLinkCapacityASes.set(0);

    }

    private void testTraffic(TrafficClassification trafficClassification, Integer validBotSetSize) {

        this.sim.logMessage(String.format("Number of times the traffic updated: %d", this.numUpdatedTrafficASes.get()));
        this.sim.logMessage(String.format("Number of zero entries for traffic factors: %d", this.numZeroTrafficFactors.get()));

        if (trafficClassification == TrafficClassification.NORMAL) {
            this.sim.logMessage(String.format("Normal: Number of null active to active paths: %d", this.numNullNormalActiveToActivePaths.get()));
            this.sim.logMessage(String.format("Normal: Number of null active to purged paths: %d", this.numNullNormalActiveToPurgedPaths.get()));
            this.sim.logMessage(String.format("Normal: Number of null purged to active paths: %d", this.numNullNormalPurgedToActivePaths.get()));
            this.sim.logMessage(String.format("Normal: Number of null purged to purged paths: %d", this.numNullNormalPurgedToPurgedPaths.get()));
        } else if (trafficClassification == TrafficClassification.CURRENT_VOLATILE) {
            this.sim.logMessage(String.format("Current Volatile: Number of null active to reactor paths: %d", this.numNullVolatileActiveToReactorPaths.get()));
            this.sim.logMessage(String.format("Current Volatile: Number of null purged to reactor paths: %d", this.numNullVolatilePurgedToReactorPaths.get()));
        } else if (trafficClassification == TrafficClassification.VOLATILE) {
            this.sim.logMessage(String.format("Volatile: Number of null active to reactor paths: %d", this.numNullVolatileActiveToReactorPaths.get()));
            this.sim.logMessage(String.format("Volatile: Number of null purged to reactor paths: %d", this.numNullVolatilePurgedToReactorPaths.get()));
        } else if (trafficClassification == TrafficClassification.BOT) {
            // this.sim.logMessage(String.format("Bot: Number of null active to active paths: %d", this.numNullBotActiveToActivePaths.get()));
            // this.sim.logMessage(String.format("Bot: Number of null active to purged paths: %d", this.numNullBotActiveToPurgedPaths.get()));
            // this.sim.logMessage(String.format("Bot: Number of null purged to active paths: %d", this.numNullBotPurgedToActivePaths.get()));
            // this.sim.logMessage(String.format("Bot: Number of null purged to purged paths: %d", this.numNullBotPurgedToPurgedPaths.get()));
            this.sim.logMessage(String.format("Bot: Number of bot to reactor times iterated in Bot traffic worker: %d", this.numBotToReactorIteratedInWorker.get()));
            this.sim.logMessage(String.format("Bot: Number of times botAllTraffic called at beginning: %d", this.getNumBotToReactorTimesBotAllTrafficCalled.get()));
            this.sim.logMessage(String.format("Bot: Number of times iterated part 1: %d", this.numBotToReactorPart1.get()));
            this.sim.logMessage(String.format("Bot: Number of times iterated part 2: %d", this.numBotToReactorPart2.get()));
            this.sim.logMessage(String.format("Bot: Number of times iterated part 3: %d", this.numBotToReactorPart3.get()));
            this.sim.logMessage(String.format("Bot: Number of bot to reactor times iterated after null check in botAllTraffic: %d", this.numBotToReactorPastPathNullCheck.get()));

            this.sim.logMessage(String.format("Bot: Number of times bot AS null in botAllTraffic: %d", this.numTimesBotASNull.get()));
            this.sim.logMessage(String.format("Bot: Number of times dest AS null in botAllTraffic: %d", this.numTimesBotDestASNull.get()));
            this.sim.logMessage(String.format("Bot: Number of times in botAllTraffic that the bot AS was the dest AS: %d", this.numTimesBotWasDestASinBotTrafficToAll.get()));
            this.sim.logMessage(String.format("Bot: Number of null bot paths total: %d", this.numNullBotCombinedPaths.get()));
            this.sim.logMessage(String.format("Bot: Number of times bot traffic went over critical to reacting link: %d", this.numTimesBotTrafficWentOverCriticalPath.get()));
            this.sim.logMessage(String.format("Bot: Size of valid bot set: %d", validBotSetSize));
            if (validBotSetSize != this.numTimesBotTrafficWentOverCriticalPath.get()) {
                this.sim.logMessage("Valid bot set size and the number of times the path went over the critical to reacting link is NOT EQUAL!");
            }
        }

        if (TrafficManager.DEEP_DEBUG) {
            for (AS tAS : this.activeTopology.valueCollection()) {
                for (int tASN : tAS.getNeighbors()) {
                    this.sim.logMessage("Active " + tAS.getASN() + ", " + tASN + ", Normal " + tAS.getTrafficOverLinkBetween(tASN));
                    this.sim.logMessage("Active " + tAS.getASN() + ", " + tASN + ", Volatile " + tAS.getVolTraffic(tASN));
                    this.sim.logMessage("Active " + tAS.getASN() + ", " + tASN + ", Current Volatile " + tAS.getCurrentVolTraffic(tASN));
                    this.sim.logMessage("Active " + tAS.getASN() + ", " + tASN + ", Bot Traffic " + tAS.getBotTraffic(tASN));
                }
                this.sim.logMessage("\n");
            }

            for (AS tAS : this.purgedTopology.valueCollection()) {
                for (int tASN : tAS.getNeighbors()) {
                    this.sim.logMessage("Purged " + tAS.getASN() + ", " + tASN + ", Normal " + tAS.getTrafficOverLinkBetween(tASN));
                    this.sim.logMessage("Purged " + tAS.getASN() + ", " + tASN + ", Volatile " + tAS.getVolTraffic(tASN));
                    this.sim.logMessage("Purged " + tAS.getASN() + ", " + tASN + ", Current Volatile " + tAS.getCurrentVolTraffic(tASN));
                    this.sim.logMessage("Purged " + tAS.getASN() + ", " + tASN + ", Bot Traffic " + tAS.getBotTraffic(tASN));
                }
                this.sim.logMessage("\n");
            }
        }

        // Reset counters

        this.numNullNormalActiveToActivePaths.set(0);
        this.numNullNormalActiveToPurgedPaths.set(0);
        this.numNullNormalPurgedToActivePaths.set(0);
        this.numNullNormalPurgedToPurgedPaths.set(0);

        this.numNullBotActiveToActivePaths.set(0);
        this.numNullBotActiveToPurgedPaths.set(0);
        this.numNullBotPurgedToActivePaths.set(0);
        this.numNullBotPurgedToPurgedPaths.set(0);
        this.numNullBotCombinedPaths.set(0);

        this.numNullVolatileActiveToReactorPaths.set(0);
        this.numNullVolatilePurgedToReactorPaths.set(0);

        this.numBotToReactorPastPathNullCheck.set(0);
        this.numBotToReactorPart1.set(0);
        this.numBotToReactorPart2.set(0);
        this.numBotToReactorPart3.set(0);
        this.numBotToReactorIteratedInWorker.set(0);
        this.numTimesBotTrafficWentOverCriticalPath.set(0);
        this.getNumBotToReactorTimesBotAllTrafficCalled.set(0);
        this.numTimesBotWasDestASinBotTrafficToAll.set(0);
        this.numTimesBotDestASNull.set(0);
        this.numTimesBotASNull.set(0);

        this.numUpdatedTrafficASes.set(0);
        this.numZeroTrafficFactors.set(0);
    }
}
