package chaos.eval.nyx.src;

import chaos.topo.AS;
import chaos.topo.BGPPath;

import java.util.concurrent.ConcurrentHashMap;

public class DisturbanceCompareTask implements Runnable {

    private AS currentAS;
    private Integer deployerASN;

    private ConcurrentHashMap<Integer, BGPPath> pathMap;
    private ConcurrentHashMap<Integer, Integer> localPrefMap;

    private DisturbanceStats disturbanceStats;

    public DisturbanceCompareTask(AS currentAS, ConcurrentHashMap<Integer, BGPPath> pathMap,
                                  ConcurrentHashMap<Integer, Integer> localPrefMap,
                                  Integer deployerASN, DisturbanceStats disturbanceStats) {
        super();

        this.currentAS = currentAS;
        this.deployerASN = deployerASN;
        this.pathMap = pathMap;
        this.localPrefMap = localPrefMap;
        this.disturbanceStats = disturbanceStats;
    }

    @Override
    public void run() {
        checkPaths();
        checkLocalPrefs();
    }

    private void checkPaths() {
        BGPPath oldPath = this.pathMap.get(this.currentAS.getASN());
        BGPPath newPath = this.currentAS.getPath(this.deployerASN);
        if (newPath == null) {
            return;
        }
        int oldPathLength = oldPath.getRealPathLength();
        int newPathLength = newPath.getRealPathLength();

        for (int j = 0; j < oldPath.getRealPathLength(); j += 1) {
            int currentOldASN = 0;
            int currentNewASN = 0;
            try {
                currentOldASN = oldPath.getPath().get(j);
                currentNewASN = newPath.getPath().get(j);
            } catch (IndexOutOfBoundsException e) {
                /* Paths are different, log stats. */
                this.setDisturbedIPsAndASs();

                int pathChange = newPathLength - oldPathLength;
                int pathIncrease = pathChange < 0 ? 0 : pathChange;

                this.setPathStats(pathChange, pathIncrease);

                break;
            }

            if (currentOldASN != 0 && currentNewASN != 0 && currentOldASN != currentNewASN) {
                /* Paths are different, log stats. */
                this.setDisturbedIPsAndASs();

                int pathChange = newPathLength - oldPathLength;
                int pathIncrease = pathChange < 0 ? 0 : pathChange;

                this.setPathStats(pathChange, pathIncrease);

                break;
            }
        }
    }

    private void checkLocalPrefs() {
        BGPPath pathToDeployer = this.currentAS.getPath(this.deployerASN);
        if (pathToDeployer == null) {
            return;
        }
        int nextHopASN = pathToDeployer.getNextHop();

        int oldRelationship = this.localPrefMap.get(this.currentAS.getASN());
        // System.out.println(String.format("Old Relationship: %d", oldRelationship));
        int newRelationship = this.currentAS.getRelationship(nextHopASN);
        // System.out.println(String.format("New Relationship: %d", this.currentAS.getRelationship(nextHopASN)));

        /*
         * Determine if the local preference changed from Peer->Provider or Customer->Peer/Provider
         */
        if (oldRelationship == 0 && newRelationship == -1) {
            setLocalPrefChange();
        } else if (oldRelationship == 1 && (newRelationship == -1 || newRelationship == 0)) {
            setLocalPrefChange();
        }
    }

    private void setPathStats(int pathChange, int pathIncrease) {
        int newPathChangeTotal = this.disturbanceStats.getPathChangeTotal() + pathChange;
        int newPathIncreaseTotal = this.disturbanceStats.getPathIncreaseTotal() + pathIncrease;

        this.disturbanceStats.setPathChangeTotal(newPathChangeTotal);
        this.disturbanceStats.setPathIncreaseTotal(newPathIncreaseTotal);
    }

    private void setLocalPrefChange() {
        int newLocalPrefTotal = this.disturbanceStats.getLocalPrefChangeTotal() + 1;

        this.disturbanceStats.setLocalPrefChangeTotal(newLocalPrefTotal);
    }

    private void setDisturbedIPsAndASs() {
        int oldDisturbedIPs = this.disturbanceStats.getNumDisturbedIPs();
        this.disturbanceStats.setNumDisturbedIPs(oldDisturbedIPs + this.currentAS.getIPCount());

        int oldDisturbedASs = this.disturbanceStats.getNumDisturbedASs();
        this.disturbanceStats.setNumDisturbedASs(oldDisturbedASs + 1);
    }
}
