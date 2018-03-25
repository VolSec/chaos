package chaos.eval.nyx.src;

import chaos.topo.AS;
import chaos.topo.BGPPath;
import gnu.trove.map.TIntObjectMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BotTargetingDeployerCollectorTask implements Runnable {

    private ConcurrentHashMap<Integer, BotInfoContainer> validBotMap;
    private TIntObjectMap<AS> activeTopology;
    private TIntObjectMap<AS> prunedTopology;
    private AS botAS;
    private Integer deployerASN;
    private Integer deployingSideASN;
    private Integer criticalSideASN;

    public BotTargetingDeployerCollectorTask(ConcurrentHashMap<Integer, BotInfoContainer> validBotMap, TIntObjectMap<AS> activeTopology, TIntObjectMap<AS> prunedTopology,
                                             AS botAS, Integer deployingSideASN, Integer criticalSideASN, Integer deployerASN) {
        super();

        this.validBotMap = validBotMap;
        this.activeTopology = activeTopology;
        this.prunedTopology = prunedTopology;
        this.botAS = botAS;
        this.deployerASN = deployerASN;
        this.deployingSideASN = deployingSideASN;
        this.criticalSideASN = criticalSideASN;
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

    @Override
    public void run() {
        BGPPath pathFromBotASToDeployingSideAS;

        if (this.botAS.isPurged()) {
            List<BGPPath> pathList = new ArrayList<>();
            int tdestActiveASN = this.deployerASN;
            pathList.clear();

            for (AS tProviderAS : botAS.getProviders()) {

                BGPPath tpath = tProviderAS.getPath(tdestActiveASN);
                if (tpath == null) {
                    continue;
                }
                BGPPath cpath = tpath.deepCopy();
                cpath.prependASToPath(tProviderAS.getASN());
                pathList.add(cpath);
            }

            pathFromBotASToDeployingSideAS = this.botAS.pathSelection(pathList);
        } else {
            pathFromBotASToDeployingSideAS = this.botAS.getPath(this.deployerASN);
        }

        if (pathFromBotASToDeployingSideAS == null) {
            return;
        }

        if (pathFromBotASToDeployingSideAS.containsLink(this.criticalSideASN, this.deployingSideASN)) {
            if (this.botAS == null) {
                System.out.println("BotAS null in bot collector task.");
            }
            BotInfoContainer botInfoContainer = new BotInfoContainer(this.botAS.getASN(), this.botAS.isPurged());
            botInfoContainer.setPathToDeployer(pathFromBotASToDeployingSideAS);
            botInfoContainer.setDeployingSideASN(this.deployingSideASN);
            this.validBotMap.put(this.botAS.getASN(), botInfoContainer);
        }
    }
}
