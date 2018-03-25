package chaos.eval.nyx.src;


public class BandwidthScenarioInfo {

    private Double originalSubscriptionFactor;
    private Double resultingSubscriptionFactor;

    private Double bandwidthTolerance;
    private Double congestionFactor;
    private Double trafficAddressedToCurrentLink;

    private Double neededBotTraffic;

    public BandwidthScenarioInfo(Double bandwidthTolerance, Double congestionFactor) {
        this.bandwidthTolerance = bandwidthTolerance;
        this.congestionFactor = congestionFactor;
    }

    public Double getNeededBotTraffic() {
        return neededBotTraffic;
    }

    public void setNeededBotTraffic(Double neededBotTraffic) {
        this.neededBotTraffic = neededBotTraffic;
    }

    public Double getBandwidthTolerance() {
        return bandwidthTolerance;
    }

    public Double getCongestionFactor() {
        return congestionFactor;
    }

    public Double getOriginalSubscriptionFactor() {
        return originalSubscriptionFactor;
    }

    public void setOriginalSubscriptionFactor(Double originalSubscriptionFactor) {
        this.originalSubscriptionFactor = originalSubscriptionFactor;
    }

    public Double getResultingSubscriptionFactor() {
        return resultingSubscriptionFactor;
    }

    public void setResultingSubscriptionFactor(Double resultingSubscriptionFactor) {
        this.resultingSubscriptionFactor = resultingSubscriptionFactor;
    }

    public Double getTrafficAddressedToCurrentLink() {
        return trafficAddressedToCurrentLink;
    }

    public void setTrafficAddressedToCurrentLink(Double trafficAddressedToCurrentLink) {
        this.trafficAddressedToCurrentLink = trafficAddressedToCurrentLink;
    }
}
