package chaos.util;

import chaos.topo.CIDR;
import chaos.topo.FIB;

public class Trie {

    private Trie zeroChild;
    private Trie oneChild;
    private CIDR map;
    private int depth;

    public Trie() {
        this.zeroChild = null;
        this.oneChild = null;
        this.map = null;
        this.depth = 0;
    }

    public Trie(Trie parent, boolean isZeroChild) {
        this.depth = parent.getDepth() + 1;
        this.zeroChild = null;
        this.oneChild = null;
        this.map = null;

        if (isZeroChild) {
            parent.zeroChild = this;
        } else {
            parent.oneChild = this;
        }
    }

    public int getDepth() {
        return this.depth;
    }

    public void add(CIDR cidrToStore) {
        this.add(cidrToStore.getValue(), cidrToStore);
    }

    private void add(int value, CIDR cidrToStore) {
        /*
         * Check depth, if we've bottomed out, stop here, also if we're LESS
         * specific than the block that is stored here, bump it out, and have it
         * start moving down the tree
         */
        if (cidrToStore.getSubnetBits() <= this.depth
                || (this.map != null && (this.map.getSubnetBits() > cidrToStore.getSubnetBits()))) {
            /*
             * If nothing is stored, easy, just add in our value
             */
            if (this.map == null) {
                this.map = cidrToStore;
            } else {
                /*
                 * otherwise kick out the more specific CIDR and insert THAT one
                 * (we can jump to our position here to save some time)
                 */
                if (this.map.getSubnetBits() > cidrToStore.getSubnetBits()) {
                    CIDR move = this.map;
                    this.map = cidrToStore;
                    this.add(move.getValue(), move);
                } else {
                    RuntimeException up = new RuntimeException("Should never be true");
                    throw up;
                }
            }
            return;
        }

        //XXX could be factored better
        int bit = value >>> (31 - this.depth);
        bit = bit & 0x01;
        if (bit == 0) {
            if (this.zeroChild != null) {
                this.zeroChild.add(value, cidrToStore);
            } else {
                this.grow(value, cidrToStore, true);
                return;
            }
        } else {
            if (this.oneChild != null) {
                this.oneChild.add(value, cidrToStore);
            } else {
                this.grow(value, cidrToStore, false);
                return;
            }
        }
    }

    private void grow(int value, CIDR cidrToStore, boolean zeroChildFork) {
        /*
         * If this node is null, we can just store and be done (yay!)
         */
        if (this.map == null) {
            this.map = cidrToStore;
            return;
        }

        /*
         * One of two things is true at this point, they differ in the next bit,
         * or one is simply a more specific version of the other
         */
        int insertBit = value >>> (31 - this.depth);
        insertBit = insertBit & 0x01;
        int existBit = this.map.getValue() >>> (31 - this.depth);
        existBit = existBit & 0x01;

        if (insertBit == existBit) {
            /*
             * One of them is just a more specific version of the other, keep
             * the less specific one here, move the more specific one down the
             * correct branch
             */
            Trie child = new Trie(this, zeroChildFork);
            if (cidrToStore.getSubnetBits() > this.map.getSubnetBits()) {
                child.add(value, cidrToStore);
            } else {
                CIDR move = this.map;
                this.map = cidrToStore;
                child.add(move.getValue(), move);
            }
            return;
        } else {
            /*
             * They differ in bits, put the new node on the correct 0/1 branch
             */
            Trie child = new Trie(this, zeroChildFork);
            child.add(value, cidrToStore);
        }
    }

    public CIDR findContainingCIDR(CIDR testCidr, CIDR prevMatch) {
        /*
         * Test if the stored CIDR contains this CIDR, if so, update the previous match
         */
        if (this.map != null) {
            if (this.map.contains(testCidr) && !this.map.equals(testCidr)) {
                if (prevMatch != null && prevMatch.getSubnetBits() > this.map.getSubnetBits()) {
                    System.err.println("odd... " + prevMatch.getSubnetBits() + " " + this.map.getSubnetBits());
                }
                prevMatch = this.map;
            }
        }

        /*
         * Fetch the bit at the current depth we're at, then fetch the correct
         * child branch of the tree
         */
        int bit = testCidr.getValue() >>> (31 - this.depth);
        bit = bit & 0x01;
        Trie childOfInterest = null;
        if (bit == 0) {
            childOfInterest = this.zeroChild;
        } else {
            childOfInterest = this.oneChild;
        }

        /*
         * If the branch is null, then we're done, return the best match we
         * have, otherwise recurse down the tree as there _might_ be a better
         * match
         */
        if (childOfInterest == null) {
            return prevMatch;
        } else {
            return childOfInterest.findContainingCIDR(testCidr, prevMatch);
        }
    }

    public CIDR resolve(int value, CIDR prevMatch) {
        /*
         * Test if the stored value contains this value, if so update the
         * previous match
         */
        if (this.map != null) {
            if (this.map.in(value)) {
                if (prevMatch != null && prevMatch.getSubnetBits() > this.map.getSubnetBits()) {
                    System.err.println("odd... " + prevMatch.getSubnetBits() + " " + this.map.getSubnetBits());
                }
                prevMatch = this.map;
            }
        }

        /*
         * Fetch the bit at the current depth we're at, then fetch the correct
         * child branch of the tree
         */
        int bit = value >>> (31 - this.depth);
        bit = bit & 0x01;
        Trie childOfInterest = null;
        if (bit == 0) {
            childOfInterest = this.zeroChild;
        } else {
            childOfInterest = this.oneChild;
        }

        /*
         * If the branch is null, then we're done, return the best match we
         * have, otherwise recurse down the tree as there _might_ be a better
         * match
         */
        if (childOfInterest == null) {
            return prevMatch;
        } else {
            return childOfInterest.resolve(value, prevMatch);
        }
    }

    public static void main(String[] args) {
        Trie self = new Trie();
        CIDR a = new CIDR("128.0.0.0/8");
        CIDR b = new CIDR("128.0.0.0/9");
        CIDR c = new CIDR("64.0.0.0/1");
        CIDR d = new CIDR("63.0.0.0/24");
        self.add(a);
        self.add(b);
        self.add(c);
        self.add(d);

        String ipStr = null;
        ipStr = "128.0.0.0";
        System.out.println("Resovling " + ipStr + " should be 128.0.0.0/9 " + self.resolve(FIB.convertStringToIPValue(ipStr), null).toString());
        ipStr = "128.0.0.128";
        System.out.println("Resovling " + ipStr + " should be 128.0.0.0/9 " + self.resolve(FIB.convertStringToIPValue(ipStr), null).toString());
        ipStr = "128.128.0.1";
        System.out.println("Resovling " + ipStr + " should be 128.0.0.0/8 " + self.resolve(FIB.convertStringToIPValue(ipStr), null).toString());
        ipStr = "64.0.0.12";
        System.out.println("Resovling " + ipStr + " should be 64.0.0.0/1 " + self.resolve(FIB.convertStringToIPValue(ipStr), null).toString());
        ipStr = "63.0.0.254";
        System.out.println("Resovling " + ipStr + " should be 63.0.0.0/24 " + self.resolve(FIB.convertStringToIPValue(ipStr), null).toString());
    }
}
