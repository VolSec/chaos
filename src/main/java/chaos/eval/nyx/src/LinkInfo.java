package chaos.eval.nyx.src;


public class LinkInfo {

    private Integer criticalSideASN;
    private Integer deployingSideASN;

    private Double subscriptionFactor;

    public LinkInfo(Integer criticalSideASN, Integer deployingSideASN) {
        this.criticalSideASN = criticalSideASN;
        this.deployingSideASN = deployingSideASN;
    }

    public Integer getCriticalSideASN() {
        return criticalSideASN;
    }

    public Integer getDeployingSideASN() {
        return deployingSideASN;
    }

    public Double getSubscriptionFactor() {
        return subscriptionFactor;
    }

    public void setSubscriptionFactor(Double subscriptionFactor) {
        this.subscriptionFactor = subscriptionFactor;
    }

}
