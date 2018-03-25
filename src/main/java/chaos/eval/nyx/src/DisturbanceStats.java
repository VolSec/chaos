package chaos.eval.nyx.src;

import com.mongodb.client.MongoCollection;
import org.bson.Document;

import java.io.IOException;
import java.io.Writer;

public class DisturbanceStats {

    private String logID;
    private Integer runNum;
    private Integer scenarioNum;
    private Integer numPairs;
    private Integer deployerASN;
    private Integer criticalASN;
    private Integer numDisturbedASs;
    private Integer numDisturbedIPs;
    private Integer pathChangeTotal;
    private Integer pathIncreaseTotal;
    private Integer localPrefChangeTotal;

    public DisturbanceStats(Integer runNum, Integer deployerASN, Integer criticalASN, Integer scenarioNum, String logID) {
        this.logID = logID;
        this.runNum = runNum;
        this.scenarioNum = scenarioNum;
        this.deployerASN = deployerASN;
        this.criticalASN = criticalASN;

        this.numPairs = 0;
        this.numDisturbedASs = 0;
        this.numDisturbedIPs = 0;
        this.pathChangeTotal = 0;
        this.pathIncreaseTotal = 0;
        this.localPrefChangeTotal = 0;
    }

    public Integer getRunNum() {
        return runNum;
    }

    public void setRunNum(Integer runNum) {
        this.runNum = runNum;
    }

    public Integer getNumPairs() {
        return numPairs;
    }

    public void setNumPairs(Integer numPairs) {
        this.numPairs = numPairs;
    }

    public Integer getDeployerASN() {
        return deployerASN;
    }

    public void setDeployerASN(Integer deployerASN) {
        this.deployerASN = deployerASN;
    }

    public Integer getCriticalASN() {
        return criticalASN;
    }

    public void setCriticalASN(Integer criticalASN) {
        this.criticalASN = criticalASN;
    }

    public Integer getNumDisturbedIPs() {
        return numDisturbedIPs;
    }

    public void setNumDisturbedIPs(Integer numDisturbedIPs) {
        this.numDisturbedIPs = numDisturbedIPs;
    }

    public Integer getNumDisturbedASs() {
        return numDisturbedASs;
    }

    public void setNumDisturbedASs(Integer numDisturbedASs) {
        this.numDisturbedASs = numDisturbedASs;
    }

    public Integer getPathChangeTotal() {
        return pathChangeTotal;
    }

    public void setPathChangeTotal(Integer pathChangeTotal) {
        this.pathChangeTotal = pathChangeTotal;
    }

    public Integer getPathIncreaseTotal() {
        return pathIncreaseTotal;
    }

    public void setPathIncreaseTotal(Integer pathIncreaseTotal) {
        this.pathIncreaseTotal = pathIncreaseTotal;
    }

    public Integer getLocalPrefChangeTotal() {
        return localPrefChangeTotal;
    }

    public void setLocalPrefChangeTotal(Integer localPrefChangeTotal) {
        this.localPrefChangeTotal = localPrefChangeTotal;
    }

    public void savePairStats(Integer pairNum, Writer disturbanceOut, MongoCollection<Document> disturbanceResultsCollection) {
        try {
            String output = String.format("LogID-%s-RunNum-%d-ScenarioNum-%d-PairNum-%d-deployingSideASN-%d-CriticalSideASN-%d-NumDisturbedASes-%d-NumDisturbedIPs-%d-PathChangeTotal-%d-PathIncreaseTotal-%d-LocalPrefChangeTotal-%d",
                    this.logID, this.runNum, this.scenarioNum, pairNum, this.deployerASN, this.criticalASN, this.numDisturbedASs, this.numDisturbedIPs,
                    this.pathChangeTotal, this.pathIncreaseTotal, this.localPrefChangeTotal);
            System.out.println(String.format("Disturbance Result: %s", output));
            disturbanceOut.write(output + "\n");
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        Document doc = new Document("LogID", this.logID)
                .append("RunNum", this.runNum)
                .append("ScenarioNum", this.scenarioNum)
                .append("PairNum", pairNum)
                .append("deployingSideASN", this.deployerASN)
                .append("CriticalSideASN", this.criticalASN)
                .append("NumDisturbedASes", this.numDisturbedASs)
                .append("NumDisturbedIPs", this.numDisturbedIPs)
                .append("PathChangeTotal", this.pathChangeTotal)
                .append("PathIncreaseTotal", this.pathIncreaseTotal)
                .append("LocalPrefChangeTotal", this.localPrefChangeTotal);
        disturbanceResultsCollection.insertOne(doc);

    }
}
