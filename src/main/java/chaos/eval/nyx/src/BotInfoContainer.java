package chaos.eval.nyx.src;

import chaos.topo.BGPPath;

import java.util.HashMap;

public class BotInfoContainer {

    private Boolean botASIsPurged;
    private Integer botASN;
    private Integer numBots;
    private HashMap<Integer, BGPPath> validDestASes;
    private BGPPath pathToDeployer;
    private Integer deployingSideASN;

    public BotInfoContainer(Integer botASN, Boolean botASIsPurged) {
        this.botASIsPurged = botASIsPurged;
        this.botASN = botASN;
        this.validDestASes = new HashMap<>();
    }

    public HashMap<Integer, BGPPath> getValidDestASes() {
        return validDestASes;
    }

    public BGPPath getPathToDeployer() {
        return pathToDeployer;
    }

    public void setPathToDeployer(BGPPath pathToDeployer) {
        this.pathToDeployer = pathToDeployer;
    }

    public Integer getDeployingSideASN() {
        return deployingSideASN;
    }

    public void setDeployingSideASN(Integer deployingSideASN) {
        this.deployingSideASN = deployingSideASN;
    }


    public void addDest(Integer destASN, BGPPath pathToDest) {
        this.validDestASes.put(destASN, pathToDest);
    }

    public Boolean getBotASIsPurged() {
        return botASIsPurged;
    }

    public void setBotASIsPurged(Boolean botASIsPurged) {
        this.botASIsPurged = botASIsPurged;
    }

    public Integer getBotASN() {
        return botASN;
    }

    public void setBotASN(Integer botASN) {
        this.botASN = botASN;
    }

    public Integer getNumBots() {
        return numBots;
    }

    public void setNumBots(Integer numBots) {
        this.numBots = numBots;
    }
}
