package chaos.sim;

import java.util.Set;

public class Config {
    public String asRelFile;
    public String ipCountFile;
    public String trafficModelFile;
    public String superASFile;
    public String trafficSplitFile;
    public String botASFile;
    public String baseLogDir;
    public boolean parallelLogging;
    public int maxNonAggressiveTopology;
    public int maxTopologySize;
    public int numThreads;
    public int randomSampleCount;
    public double botThresholdEpsilon;
    public int defaultNumTestRuns;
    public int defaultDeployStart;
    public int defaultDeployStop;
    public int defaultDeployStep;
    public int defaultFigureOfMerit;
    public boolean runWithFixedDeployerCriticalPair;
    public Set<Integer> fixedDeployerCriticalPair;
    public boolean debug;
    public String mongoDBURI;
    public boolean skipLogging;
    public boolean ignoreOnePathLimit;
    public boolean ignorePruning;
    public boolean ASDataCollection;
    public boolean computeBandwidth;
    public boolean capacityDataCollection;
    public boolean preAttackSFDataCollection;
    public boolean runWithDisturbance;
    public boolean runWithBots;
    public boolean runWithFullyDistributedBots;
    public boolean targetDeployer;
    public boolean blacklistCongested;
    public boolean testAdvertisementOnly;
    public boolean multiCritical;
    public boolean linkSearch;
    public int linkSearchMaxIterations;
    public Set<Double> congestionFactors;
    public Set<Double> bandwidthTolerances;
    public Set<Double> botThresholds;

    @Override
    public String toString() {
        return "Config{" +
                "asRelFile='" + asRelFile + '\'' +
                ", ipCountFile='" + ipCountFile + '\'' +
                ", trafficModelFile='" + trafficModelFile + '\'' +
                ", superASFile='" + superASFile + '\'' +
                ", trafficSplitFile='" + trafficSplitFile + '\'' +
                ", botASFile='" + botASFile + '\'' +
                ", baseLogDir='" + baseLogDir + '\'' +
                ", parallelLogging=" + parallelLogging +
                ", maxNonAggressiveTopology=" + maxNonAggressiveTopology +
                ", maxTopologySize=" + maxTopologySize +
                ", numThreads=" + numThreads +
                ", randomSampleCount=" + randomSampleCount +
                ", botThresholdEpsilon=" + botThresholdEpsilon +
                ", defaultNumTestRuns=" + defaultNumTestRuns +
                ", defaultDeployStart=" + defaultDeployStart +
                ", defaultDeployStop=" + defaultDeployStop +
                ", defaultDeployStep=" + defaultDeployStep +
                ", defaultFigureOfMerit=" + defaultFigureOfMerit +
                ", runWithFixedDeployerCriticalPair=" + runWithFixedDeployerCriticalPair +
                ", fixedDeployerCriticalPair=" + fixedDeployerCriticalPair +
                ", debug=" + debug +
                ", mongoDBURI='" + mongoDBURI + '\'' +
                ", skipLogging=" + skipLogging +
                ", ignoreOnePathLimit=" + ignoreOnePathLimit +
                ", ignorePruning=" + ignorePruning +
                ", ASDataCollection=" + ASDataCollection +
                ", computeBandwidth=" + computeBandwidth +
                ", capacityDataCollection=" + capacityDataCollection +
                ", preAttackSFDataCollection=" + preAttackSFDataCollection +
                ", runWithDisturbance=" + runWithDisturbance +
                ", runWithBots=" + runWithBots +
                ", runWithFullyDistributedBots=" + runWithFullyDistributedBots +
                ", targetDeployer=" + targetDeployer +
                ", blacklistCongested=" + blacklistCongested +
                ", testAdvertisementOnly=" + testAdvertisementOnly +
                ", linkSearchMaxIterations=" + linkSearchMaxIterations +
                ", congestionFactors=" + congestionFactors +
                ", bandwidthTolerances=" + bandwidthTolerances +
                ", botThresholds=" + botThresholds +
                '}';
    }
}
