package chaos.topo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CIDR {

    public static final Pattern cidrPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)/(\\d+)");
    public static final Pattern ipPattern = Pattern.compile("(\\d+)\\.(\\d+)\\.(\\d+)\\.(\\d+)");

    private int value;
    private int subnetBits;
    private int mask;

    private String origString;

    public CIDR(String cidrStr) {
        Matcher cidrMatch = CIDR.cidrPattern.matcher(cidrStr);
        if (!cidrMatch.find()) {
            throw new RuntimeException("Bad CIDR string: " + cidrStr);
        }

        /*
         * Build our actual int values
         */
        this.subnetBits = Integer.parseInt(cidrMatch.group(5));
        this.value = CIDR.parseIPMatchToInt(cidrMatch);

        /*
         * Actually do the masking
         */
        int maskInt = -1;
        // TODO check for correctness
        this.mask = maskInt << (32 - this.subnetBits);
        this.value = this.value & this.mask;

        this.origString = cidrStr;
    }

    private CIDR(CIDR cloneCidr) {
        this.value = cloneCidr.value;
        this.subnetBits = cloneCidr.subnetBits;
        this.mask = cloneCidr.mask;
        this.origString = cloneCidr.origString;
    }

    public int getMask() {
        return this.mask;
    }

    public int getValue() {
        return this.value;
    }

    public int getSubnetBits() {
        return this.subnetBits;
    }

    public String toString() {
        return this.origString;
    }

    public static int parseIPMatchToInt(Matcher conformMatch) {
        byte[] a = CIDR.intToByteArray(Integer.parseInt(conformMatch.group(1)));
        byte[] b = CIDR.intToByteArray(Integer.parseInt(conformMatch.group(2)));
        byte[] c = CIDR.intToByteArray(Integer.parseInt(conformMatch.group(3)));
        byte[] d = CIDR.intToByteArray(Integer.parseInt(conformMatch.group(4)));
        byte[] subnetBytes = new byte[4];
        subnetBytes[0] = a[0];
        subnetBytes[1] = b[0];
        subnetBytes[2] = c[0];
        subnetBytes[3] = d[0];

        return CIDR.byteArrayToInt(subnetBytes);
    }

    public boolean in(String ipAddrStr) {
        Matcher ipMatch = CIDR.ipPattern.matcher(ipAddrStr);
        if (!ipMatch.find()) {
            throw new RuntimeException("Bad IP string: " + ipAddrStr);
        }

        int ipVal = CIDR.parseIPMatchToInt(ipMatch);
        ipVal = ipVal & this.mask;

        return this.value == ipVal;
    }

    public boolean in(int ipAddr) {
        ipAddr = ipAddr & this.mask;
        return ipAddr == this.value;
    }

    public boolean contains(CIDR possibleSubCIDR) {
        if (possibleSubCIDR.mask < this.mask) {
            return false;
        }

        int tempVal = possibleSubCIDR.value & this.mask;
        return tempVal == this.value;
    }

    public boolean adjacent(CIDR otherCIDR) {
        if (otherCIDR.getSubnetBits() != this.getSubnetBits()) {
            return false;
        }

        int shorterMask = this.mask << 1;
        int lhsPrimaryBits = this.value & shorterMask;
        int rhsPrimaryBits = otherCIDR.value & shorterMask;

        if (lhsPrimaryBits != rhsPrimaryBits) {
            return false;
        }

        return this.value != otherCIDR.value;
    }

    public CIDR getStepUpMerge() {
        CIDR outCIDR = new CIDR(this);
        outCIDR.subnetBits--;
        outCIDR.mask = outCIDR.mask << 1;
        outCIDR.value = outCIDR.value & outCIDR.mask;
        outCIDR.origString += " STEP UP";
        return outCIDR;
    }

    private static int byteArrayToInt(byte[] b) {
        int value = 0;
        for (int i = 0; i < 4; i++) {
            int shift = (4 - 1 - i) * 8;
            value += (b[i] & 0x000000FF) << shift;
        }
        return value;
    }

    private static byte[] intToByteArray(int a) {
        byte[] ret = new byte[4];
        ret[0] = (byte) (a & 0xFF);
        ret[1] = (byte) ((a >> 8) & 0xFF);
        ret[2] = (byte) ((a >> 16) & 0xFF);
        ret[3] = (byte) ((a >> 24) & 0xFF);
        return ret;
    }

    public boolean equals(Object rhs) {
        if (!(rhs instanceof CIDR)) {
            return false;
        }

        CIDR rhsCidr = (CIDR) rhs;
        return this.equals(rhsCidr);
    }

    public boolean equals(CIDR rhsCidr) {
        return this.value == rhsCidr.value && this.subnetBits == rhsCidr.subnetBits;
    }

    public int hashCode() {
        return this.value * this.subnetBits;
    }

    public static void main(String args[]) {
        CIDR test = new CIDR("1.0.0.0/8");
        System.out.println(test.in("255.0.0.0"));
        System.out.println(test.in("1.2.3.4"));
    }

}
