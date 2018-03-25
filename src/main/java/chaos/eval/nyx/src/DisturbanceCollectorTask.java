package chaos.eval.nyx.src;

import chaos.topo.AS;
import chaos.topo.BGPPath;

import java.util.concurrent.ConcurrentHashMap;

public class DisturbanceCollectorTask implements Runnable {

    private AS currentAS;
    private Integer deployerASN;

    private ConcurrentHashMap<Integer, BGPPath> pathMap;
    private ConcurrentHashMap<Integer, Integer> localPrefMap;

    public DisturbanceCollectorTask(AS currentAS, ConcurrentHashMap<Integer, BGPPath> pathMap, Integer deployerASN,
                                    ConcurrentHashMap<Integer, Integer> localPrefMap) {
        super();

        this.currentAS = currentAS;
        this.deployerASN = deployerASN;
        this.pathMap = pathMap;
        this.localPrefMap = localPrefMap;
    }

    @Override
    public void run() {
        Integer currentASN = this.currentAS.getASN();
        BGPPath pathToDeployer = this.currentAS.getPath(this.deployerASN);
        if (pathToDeployer == null) {
            return;
        }

        this.pathMap.put(currentASN, pathToDeployer);

        Integer nextHopASN = pathToDeployer.getNextHop();
        this.localPrefMap.put(currentASN, this.currentAS.getRelationship(nextHopASN));
    }
}
