package chaos.sim;

import chaos.topo.AS;
import chaos.topo.ASTopoParser;
import chaos.topo.BGPPath;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;

public class BGPMaster {

    public enum WorkType {
        Adv, Process
    }

    private Chaos sim;
    private int blockCount;
    private Semaphore workSem;
    private Semaphore completeSem;
    private Queue<Set<AS>> workQueue;
    private WorkType currentWorkType;
    private final int WORK_BLOCK_SIZE = 40;
    public boolean REPORT_TIME = true;
    private PerformanceLogger perfLog = null;

    public BGPMaster(Chaos sim) {
        this.sim = sim;
        this.workSem = new Semaphore(0);
        this.completeSem = new Semaphore(0);
        this.workQueue = new LinkedBlockingQueue<Set<AS>>();
        this.currentWorkType = WorkType.Process;
    }

    public BGPMaster(int blockCount) {
        this.blockCount = blockCount;
        this.workSem = new Semaphore(0);
        this.completeSem = new Semaphore(0);
        this.workQueue = new LinkedBlockingQueue<Set<AS>>();
        this.currentWorkType = WorkType.Process;
    }

    @SuppressWarnings("unchecked")
    public TIntObjectMap<AS>[] buildBGPConnection(String wardenFile, AS.AvoidMode avoidMode,
                                                       AS.ReversePoisonMode poisonMode,
                                                       boolean largeMemEnv) throws IOException {

        /*
         * Build AS map
         */
        TIntObjectMap<AS> usefulASMap = ASTopoParser.doNetworkBuild(this.sim, wardenFile, avoidMode, poisonMode);
        TIntObjectMap<AS> prunedASMap = null;
        if (this.sim.getConfig().ignorePruning) {
            prunedASMap = new TIntObjectHashMap<AS>(0);
        } else {
            prunedASMap = ASTopoParser.doNetworkPrune(this.sim, usefulASMap, largeMemEnv);
        }

        this.sim.logMessage("Live topo size: " + usefulASMap.size());
        this.sim.logMessage("Pruned topo size: " + prunedASMap.size());

        /*
         * Give everyone their self network
         */
        for (AS tAS : usefulASMap.valueCollection()) {
            tAS.advPath(new BGPPath(tAS.getASN()));
        }

        this.driveBGPProcessing(usefulASMap);

        this.verifyConnected(usefulASMap);

        //self.tellDone();
        TIntObjectMap<AS>[] retArray = new TIntObjectMap[2];
        retArray[0] = usefulASMap;
        retArray[1] = prunedASMap;
        return retArray;
    }

    public TIntObjectMap<AS>[] buildBGPConnection(boolean largeMemEnv) throws IOException {

        /*
         * Build AS map
         */
        TIntObjectMap<AS> usefulASMap = ASTopoParser.doNetworkBuild(this.sim);
        TIntObjectMap<AS> prunedASMap = null;
        if (this.sim.getConfig().ignorePruning) {
            prunedASMap = new TIntObjectHashMap<AS>(0);
        } else {
            prunedASMap = ASTopoParser.doNetworkPrune(this.sim, usefulASMap, largeMemEnv);
        }

        this.sim.logMessage("Live topo size: " + usefulASMap.size());
        this.sim.logMessage("Pruned topo size: " + prunedASMap.size());

        /*
         * Give everyone their self network
         */
        for (AS tAS : usefulASMap.valueCollection()) {
            tAS.advPath(new BGPPath(tAS.getASN()));
        }

        this.driveBGPProcessing(usefulASMap);

        this.verifyConnected(usefulASMap);

        //self.tellDone();
        TIntObjectMap<AS>[] retArray = new TIntObjectMap[2];
        retArray[0] = usefulASMap;
        retArray[1] = prunedASMap;
        return retArray;
    }

    @SuppressWarnings("unchecked")
    public TIntObjectMap<AS>[] buildASObjectsOnly(Chaos sim, String wardenFile) throws IOException {
        /*
         * Build AS map
         */
        TIntObjectMap<AS> usefulASMap = ASTopoParser.doNetworkBuild(this.sim, wardenFile, AS.AvoidMode.NONE,
                AS.ReversePoisonMode.NONE);
        TIntObjectMap<AS> prunedASMap = ASTopoParser.doNetworkPrune(this.sim, usefulASMap, false);

        TIntObjectMap<AS>[] retArray = new TIntObjectMap[2];
        retArray[0] = usefulASMap;
        retArray[1] = prunedASMap;
        return retArray;
    }

    public <T extends AS> void driveBGPProcessing(TIntObjectMap<T> activeMap) {
        if (this.perfLog != null) {
            this.perfLog.resetTimer();
        }

        /*
         * dole out ases into blocks
         */
        List<Set<AS>> asBlocks = new LinkedList<Set<AS>>();
        int currentBlockSize = 0;
        Set<AS> currentSet = new HashSet<AS>();
        for (AS tAS : activeMap.valueCollection()) {
            currentSet.add(tAS);
            currentBlockSize++;

            /*
             * if it's a full block, send it to the list
             */
            if (currentBlockSize >= this.WORK_BLOCK_SIZE) {
                asBlocks.add(currentSet);
                currentSet = new HashSet<AS>();
                currentBlockSize = 0;
            }
        }
        /*
         * add the partial set at the end if it isn't empty
         */
        if (currentSet.size() > 0) {
            asBlocks.add(currentSet);
        }

        /*
         * build the master and slaves, spin the slaves up
         */
        this.blockCount = asBlocks.size();
        List<Thread> slaveThreads = new LinkedList<Thread>();
        for (int counter = 0; counter < this.sim.getConfig().numThreads; counter++) {
            slaveThreads.add(new Thread(new BGPSlave(this), "BGPProcessingWorker"));
        }
        for (Thread tThread : slaveThreads) {
            //tThread.setDaemon(true);
            tThread.start();
        }

        long bgpStartTime = System.currentTimeMillis();
        if (this.REPORT_TIME) {
            this.sim.logMessage("Starting up the BGP processing....");
        }

        boolean stuffToDo = true;
        boolean skipToMRAI = false;
        while (stuffToDo || skipToMRAI) {
            stuffToDo = false;
            skipToMRAI = false;

            /*
             * dole out work to slaves
             */
            for (Set<AS> tempBlock : asBlocks) {
                this.addWork(tempBlock);
            }

            /*
             * Wait till this round is done
             */
            try {
                this.wall();
            } catch (InterruptedException e) {
                e.printStackTrace();
                System.exit(-2);
            }

            /*
             * check if nodes still have stuff to do
             */
            int workToDo = 0;
            int dirtyRoutes = 0;
            for (AS tAS : activeMap.valueCollection()) {
                if (tAS.hasWorkToDo()) {
                    stuffToDo = true;
                    workToDo++;
                }
                if (tAS.hasDirtyPrefixes()) {
                    skipToMRAI = true;
                    dirtyRoutes++;
                }
            }

            /*
             * If we have no pending BGP messages, release all pending updates,
             * this is slightly different from a normal MRAI, but it gets the
             * point
             */
            if (!stuffToDo && skipToMRAI) {
                this.currentWorkType = WorkType.Adv;
            } else {
                this.currentWorkType = WorkType.Process;
            }
        }

        bgpStartTime = System.currentTimeMillis() - bgpStartTime;
        if (this.REPORT_TIME) {
            this.sim.logMessage("BGP done, this took: " + (bgpStartTime / 60000) + " minutes.");
        }

        this.sim.logMessage("Shutting down BGP Processing Worker threads...");
        for (Thread tThread : slaveThreads) {
            tThread.interrupt();
            try {
                tThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (this.perfLog != null) {
            this.perfLog.logTime("BGP Processing run");
        }
    }

    public PerformanceLogger getPerfLog() {
        return perfLog;
    }

    public void setPerfLog(PerformanceLogger perfLog) {
        this.perfLog = perfLog;
    }

    public void addWork(Set<AS> workSet) {
        this.workQueue.add(workSet);
        this.workSem.release();
    }

    public Set<AS> getWork() throws InterruptedException {

        this.workSem.acquire();
        return this.workQueue.poll();
    }

    public WorkType getCurentWorkType() {
        return this.currentWorkType;
    }

    public void reportWorkDone() {
        this.completeSem.release();
    }

    public void wall() throws InterruptedException {
        for (int counter = 0; counter < this.blockCount; counter++) {
            this.completeSem.acquire();
        }
    }

    private void verifyConnected(TIntObjectMap<AS> transitAS) {
        long startTime = System.currentTimeMillis();
        this.sim.logMessage("Starting connection verification");

        double examinedPaths = 0.0;
        double workingPaths = 0.0;

        for (AS tAS : transitAS.valueCollection()) {
            for (AS tDest : transitAS.valueCollection()) {
                if (tDest.getASN() == tAS.getASN()) {
                    continue;
                }

                examinedPaths++;
                if (tAS.getPath(tDest.getASN()) != null) {
                    workingPaths++;
                }
            }
        }

        startTime = System.currentTimeMillis() - startTime;
        this.sim.logMessage("Verification done in: " + startTime);
        this.sim.logMessage("Paths exist for " + workingPaths + " of " + examinedPaths + " possible ("
                + (workingPaths / examinedPaths * 100.0) + "%)");
    }
}
