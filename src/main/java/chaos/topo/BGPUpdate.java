package chaos.topo;

import java.io.Serializable;

/**
 * Class that represents a BGP update message. In C this would be a struct that
 * contains a union based on if it is an explicit withdrawal or an advertisement
 *
 * @author pendgaft
 */
public class BGPUpdate implements Serializable {

    /**
     * Serialization ID
     */
    private static final long serialVersionUID = 4978430406501402116L;

    private int withdrawlDest;
    private AS withrdawlSource;
    private BGPPath path;
    private boolean withdrawal;

    /**
     * Constructor used to build an advertisement message.
     *
     * @param path - the path we're advertising
     */
    public BGPUpdate(BGPPath path) {
        this.path = path;
        this.withdrawal = false;
    }

    /**
     * Constructor used to build a withdrawal update message.
     */
    public BGPUpdate(int dest, AS src) {
        this.withdrawlDest = dest;
        this.withrdawlSource = src;
        this.withdrawal = true;
    }

    /**
     * Predicate to test if this is a withdrawal message or advertisement
     *
     * @return - true if this is an explicit withdrawal message, false if this
     * is advertising a new prefix
     */
    public boolean isWithdrawal() {
        return this.withdrawal;
    }

    /**
     * Fetches the route being advertised. This functions so long as this is not
     * an explicit withdrawal message.
     *
     * @return - the BGP route being advertised
     */
    public BGPPath getPath() {
        /*
         * Sanity check that this isn't an explicit withdrawal message
         */
        if (this.isWithdrawal()) {
            throw new RuntimeException("Attempted to fetch path from explcit withdrawal!");
        }

        return this.path;
    }

    /**
     * Fetches the destination that the update is reporting a loss of route to.
     *
     * @return - the ASN of the networks we lost all routes to
     */
    public int getWithdrawnDest() {
        return this.withdrawlDest;
    }

    /**
     * Fetches the ASN that is reporting the loss of a route (which peer sent
     * this message).
     *
     * @return - the ASN of the peer that advertised this explicit withdrawal to
     * us
     */
    public AS getWithdrawer() {
        return this.withrdawlSource;
    }
}
