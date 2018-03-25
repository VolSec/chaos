package chaos.eval.nyx.src;

import java.util.Set;

/**
 * Contains data set after each scenario and returned to the caller.
 */
public class ScenarioInfo {
    private Boolean success;
    private Double largestModifiedSubscriptionFactor;
    private Set<LinkInfo> modifiedCriticalToDeployingLinks;
    private Set<LinkInfo> originalCriticalToDeployingLinks;

    public ScenarioInfo() {
    }

    public Set<LinkInfo> getModifiedCriticalToDeployingLinks() {
        return modifiedCriticalToDeployingLinks;
    }

    public void setModifiedCriticalToDeployingLinks(Set<LinkInfo> modifiedCriticalToDeployingLinks) {
        this.modifiedCriticalToDeployingLinks = modifiedCriticalToDeployingLinks;
    }

    public Set<LinkInfo> getOriginalCriticalToDeployingLinks() {
        return originalCriticalToDeployingLinks;
    }

    public void setOriginalCriticalToDeployingLinks(Set<LinkInfo> originalCriticalToDeployingLinks) {
        this.originalCriticalToDeployingLinks = originalCriticalToDeployingLinks;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public Double getLargestModifiedSubscriptionFactor() {
        return largestModifiedSubscriptionFactor;
    }

    public void setLargestModifiedSubscriptionFactor(Double largestModifiedSubscriptionFactor) {
        this.largestModifiedSubscriptionFactor = largestModifiedSubscriptionFactor;
    }
}
