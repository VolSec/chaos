package chaos.eval.nyx.src;

import chaos.sim.Chaos;
import chaos.topo.AS;
import chaos.topo.BGPPath;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.Writer;
import java.util.List;
import java.util.concurrent.*;

public class DisturbanceRunner {

    private Chaos sim;

    private Integer deployerASN;
    private ConcurrentHashMap<Integer, BGPPath> disturbancePathMap;
    private ConcurrentHashMap<Integer, Integer> disturbanceLocalPrefMap;
    private static final int WAIT_SECONDS = 100;

    public DisturbanceRunner(Chaos sim, Integer deployerASN) {
        this.sim = sim;
        this.deployerASN = deployerASN;

        this.disturbancePathMap = new ConcurrentHashMap<>();
        this.disturbanceLocalPrefMap = new ConcurrentHashMap<>();
    }

    public void buildOriginalMaps(Boolean parallelBuild) {
        ExecutorService threadPool = null;

        if (parallelBuild) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("OriginalDisturbanceMapBuilderTask-%d")
                    .setDaemon(true)
                    .build();
            threadPool = Executors.newFixedThreadPool(this.sim.getConfig().numThreads, threadFactory);
        }

        for (AS tAS : this.sim.getActiveTopology().valueCollection()) {
            if (parallelBuild) {
                threadPool.execute(new DisturbanceCollectorTask(tAS, this.disturbancePathMap, this.deployerASN, this.disturbanceLocalPrefMap));
            } else {
                DisturbanceCollectorTask task = new DisturbanceCollectorTask(tAS, this.disturbancePathMap, this.deployerASN, this.disturbanceLocalPrefMap);
                task.run();
            }
        }

        if (parallelBuild) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                final List<Runnable> rejected = threadPool.shutdownNow();
                System.out.println(String.format("Rejected tasks: %d", rejected.size()));
            }
        }

        if (parallelBuild) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("OriginalDisturbanceMapBuilderTask-%d")
                    .setDaemon(true)
                    .build();
            threadPool = Executors.newFixedThreadPool(this.sim.getConfig().numThreads, threadFactory);
        }

        for (AS tAS : this.sim.getPrunedTopology().valueCollection()) {
            if (parallelBuild) {
                threadPool.execute(new DisturbanceCollectorTask(tAS, this.disturbancePathMap, this.deployerASN, this.disturbanceLocalPrefMap));
            } else {
                DisturbanceCollectorTask task = new DisturbanceCollectorTask(tAS, this.disturbancePathMap, this.deployerASN, this.disturbanceLocalPrefMap);
                task.run();
            }
        }

        if (parallelBuild) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                final List<Runnable> rejected = threadPool.shutdownNow();
                System.out.println(String.format("Rejected tasks: %d", rejected.size()));
            }
        }
    }

    public void compareMapsToCurrentState(Integer runNum, Integer pairNum, Integer deployerASN, Integer movingASN,
                                          Boolean parallelComparisons, Writer disturbanceOut,
                                          MongoCollection<Document> disturbanceResultsCollection, Integer scenarioNum,
                                          String logID) {
        DisturbanceStats ds = new DisturbanceStats(runNum, deployerASN, movingASN, scenarioNum, logID);
        ExecutorService threadPool = null;

        if (parallelComparisons) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("DisturbanceMapComparerTask-%d")
                    .setDaemon(true)
                    .build();
            threadPool = Executors.newFixedThreadPool(this.sim.getConfig().numThreads, threadFactory);
        }

        for (AS tAS : this.sim.getActiveTopology().valueCollection()) {

            if (parallelComparisons) {
                threadPool.execute(new DisturbanceCompareTask(tAS, this.disturbancePathMap,
                        this.disturbanceLocalPrefMap,
                        this.deployerASN, ds));
            } else {
                DisturbanceCompareTask task = new DisturbanceCompareTask(tAS, this.disturbancePathMap,
                        this.disturbanceLocalPrefMap, this.deployerASN, ds);
                task.run();
            }
        }

        if (parallelComparisons) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                final List<Runnable> rejected = threadPool.shutdownNow();
                System.out.println(String.format("Rejected tasks: %d", rejected.size()));
            }
        }

        if (parallelComparisons) {
            final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                    .setNameFormat("DisturbanceMapComparerTask-%d")
                    .setDaemon(true)
                    .build();
            threadPool = Executors.newFixedThreadPool(this.sim.getConfig().numThreads, threadFactory);
        }

        for (AS tAS : this.sim.getPrunedTopology().valueCollection()) {
            if (parallelComparisons) {
                threadPool.execute(new DisturbanceCompareTask(tAS, this.disturbancePathMap,
                        this.disturbanceLocalPrefMap,
                        this.deployerASN, ds));
            } else {
                DisturbanceCompareTask task = new DisturbanceCompareTask(tAS, this.disturbancePathMap,
                        this.disturbanceLocalPrefMap, this.deployerASN, ds);
                task.run();
            }
        }

        if (parallelComparisons) {
            threadPool.shutdown();
            try {
                threadPool.awaitTermination(WAIT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                final List<Runnable> rejected = threadPool.shutdownNow();
                System.out.println(String.format("Rejected tasks: %d", rejected.size()));
            }
        }

        ds.savePairStats(pairNum, disturbanceOut, disturbanceResultsCollection);
    }
}
