package chaos.eval.nyx.src;

import chaos.topo.AS;
import chaos.topo.BGPPath;

/**
 * Contains the information needed by the ReactiveEngine to run the selective advertisement scenario and selective advertisement and path lining scenarios.
 */
public class ModifiedPathTestInfo {

    private BGPPath pathFromCriticalToDeployer;
    private AS lastHopOnPathFromCriticalToDeployer;

    public ModifiedPathTestInfo(BGPPath pathFromCriticalToDeployer, AS lastHopOnPathFromCriticalToDeployer) {
        this.pathFromCriticalToDeployer = pathFromCriticalToDeployer;
        this.lastHopOnPathFromCriticalToDeployer = lastHopOnPathFromCriticalToDeployer;
    }

    public BGPPath getPathFromCriticalToDeployer() {
        return pathFromCriticalToDeployer;
    }

    public void setPathFromCriticalToDeployer(BGPPath pathFromCriticalToDeployer) {
        this.pathFromCriticalToDeployer = pathFromCriticalToDeployer;
    }

    public AS getLastHopOnPathFromCriticalToDeployer() {
        return lastHopOnPathFromCriticalToDeployer;
    }

    public void setLastHopOnPathFromCriticalToDeployer(AS lastHopOnPathFromCriticalToDeployer) {
        this.lastHopOnPathFromCriticalToDeployer = lastHopOnPathFromCriticalToDeployer;
    }
}
