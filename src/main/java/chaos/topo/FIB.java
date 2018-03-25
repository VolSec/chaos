package chaos.topo;

import chaos.util.Trie;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FIB {

    private Trie tableRoot;
    private HashMap<String, Integer> cidrToASNMap;
    private HashMap<Integer, Integer> ASNToNumIPsMap;

    private HashSet<CIDR> reservedSet;

    public static final int RESERVED_INT = -1;
    public static final int INTERNAL_INT = -2;
    private static final Pattern originPattern = Pattern.compile("(\\d+\\.\\d+\\.\\d+\\.\\d+/\\d+),(.+)");

    public FIB(String configFile) throws IOException {
        this.tableRoot = new Trie();
        this.cidrToASNMap = new HashMap<String, Integer>();
        this.ASNToNumIPsMap = new HashMap<Integer, Integer>();
        this.reservedSet = new HashSet<CIDR>();
        this.populateReservedRanges();

        BufferedReader configBuff = new BufferedReader(new FileReader(configFile));
        int reservedRejection = 0;
        while (configBuff.ready()) {
            String line = configBuff.readLine().trim();
            Matcher lineMatcher = FIB.originPattern.matcher(line);
            if (lineMatcher.find()) {
                /*
                 * Duplicates strike me as odd, for now yell in a loud manner
                 */
                if (this.cidrToASNMap.containsKey(lineMatcher.group(1))) {
                    System.out.println("Duplicate! " + lineMatcher.group(1));
                    continue;
                }

                /*
                 * Add to the table and the Trie
                 */

                try {
                    if (!this.storeMapping(lineMatcher.group(1), Integer.parseInt(lineMatcher.group(2).replaceAll("\\{", "").replaceAll("\\}", "")), false)) {
                        reservedRejection++;
                    }
                } catch (NumberFormatException e) {
                    System.out.printf("ASN %s too large to fit in an int.\n", lineMatcher.group(2));
                }
            }
        }
        configBuff.close();

        System.out.println("Rejected " + reservedRejection + " that were inside a reserved range.");
    }

    private boolean storeMapping(String cidrStr, int resultInt, boolean isReserved) {
        CIDR tCidr = new CIDR(cidrStr);

        if (isReserved) {
            this.reservedSet.add(tCidr);
        } else {
            for (CIDR tReserved : this.reservedSet) {
                if (tReserved.contains(tCidr)) {
                    return false;
                }
            }
        }

        this.cidrToASNMap.put(cidrStr, resultInt);
        this.tableRoot.add(tCidr);
        return true;
    }

    private void populateReservedRanges() {
        this.storeMapping("0.0.0.0/8", FIB.RESERVED_INT, true);
        this.storeMapping("10.0.0.0/8", FIB.INTERNAL_INT, true);
        this.storeMapping("100.64.0.0/10", FIB.INTERNAL_INT, true);
        this.storeMapping("127.0.0.0/8", FIB.RESERVED_INT, true);
        this.storeMapping("169.254.0.0/16", FIB.RESERVED_INT, true);
        this.storeMapping("172.16.0.0/12", FIB.INTERNAL_INT, true);
        this.storeMapping("192.0.0.0/24", FIB.INTERNAL_INT, true);
        this.storeMapping("192.88.99.0/24", FIB.RESERVED_INT, true);
        this.storeMapping("192.168.0.0/16", FIB.INTERNAL_INT, true);
        this.storeMapping("198.18.0.0/15", FIB.INTERNAL_INT, true);
        this.storeMapping("198.51.100.0/24", FIB.RESERVED_INT, true);
        this.storeMapping("203.0.113.0/24", FIB.RESERVED_INT, true);
        this.storeMapping("224.0.0.0/4", FIB.RESERVED_INT, true);
        this.storeMapping("240.0.0.0/4", FIB.RESERVED_INT, true);
    }

    public int[] resolveASs(String[] IPs) {
        int[] ASNs = new int[IPs.length];
        for (int i = 0; i < IPs.length; i++) {
            ASNs[i] = this.resolveAS(IPs[i]);
        }
        return ASNs;
    }

    public int resolveAS(String ipAddressString) {
        int ipValue = FIB.convertStringToIPValue(ipAddressString);
        CIDR cidrMatch = this.tableRoot.resolve(ipValue, null);
        if (cidrMatch == null) {
            return 0;
        } else {
            return this.cidrToASNMap.get(cidrMatch.toString());
        }
    }

    public int findContainingCIDR(CIDR testCidr) {
        CIDR cidrMatch = this.tableRoot.findContainingCIDR(testCidr, null);
        if (cidrMatch == null) {
            return 0;
        } else {
            return this.cidrToASNMap.get(cidrMatch.toString());
        }
    }

    public static int convertStringToIPValue(String incValue) {
        Matcher ipMatch = CIDR.ipPattern.matcher(incValue);
        if (!ipMatch.find()) {
            throw new RuntimeException("Bad IP string: " + incValue);
        }

        int ipVal = CIDR.parseIPMatchToInt(ipMatch);
        return ipVal;
    }

    public void countIpsPerAs() {
        /*
         * Count IPs in each AS
         */
        for (String cidrStr : this.cidrToASNMap.keySet()) {
            CIDR cidr = new CIDR(cidrStr);
            int numIPs = (int) Math.pow(2, 32 - cidr.getSubnetBits());
            int ASN = this.cidrToASNMap.get(cidrStr);
            /*
             * Check for larger ASs which claim this IP space
             * There are three options:
             * 		1. This CIDR is not contained in another CIDR, so simply update the count of ASN
             * 		2. This CIDR is contained in another CIDR not owned by the same AS, so we
             * 			add the count to the owning AS, and subtract from the larger AS
             * 		3. This CIDR is contained in another CIDR owned by the same AS, so we
             * 			need do nothing because we would add and subtract the same number
             * Both cases where we need to do something will have belongsToAsn != ASN
             */
            int belongsToAsn = this.findContainingCIDR(cidr);
            if (belongsToAsn != ASN) {
                /*
                 * We need to add the count to the real owning AS either way
                 */
                Integer currentCount = this.ASNToNumIPsMap.get(ASN);
                if (currentCount == null) {
                    this.ASNToNumIPsMap.put(ASN, numIPs);
                } else {
                    this.ASNToNumIPsMap.put(ASN, currentCount + numIPs);
                }

                if (belongsToAsn != 0) {
                    /*
                     * Subtract count from larger AS if it is not the same AS
                     */
                    currentCount = this.ASNToNumIPsMap.get(belongsToAsn);
                    if (currentCount == null) {
                        this.ASNToNumIPsMap.put(belongsToAsn, -1 * numIPs);
                    } else {
                        this.ASNToNumIPsMap.put(belongsToAsn, currentCount - numIPs);
                    }

                }
            }
        }
    }

    /**
     * Return the number of IPs contained in an AS according to its subnet mask
     *
     * @return the number of IPs in the AS, or -1 if the ASN was not found in the map
     */
    public int getNumIPs(int ASN) {
        Integer retInt = this.ASNToNumIPsMap.get(ASN);
        if (retInt == null) {
            return 0;
        }
        return retInt;
    }

    public static void main(String args[]) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: cmd <cidrs_to_asn.txt> <bot-ips.txt>");
            return;
        }

        FIB self = new FIB(args[0]);
        File bot_ips_file = new File(args[1]);

        PrintWriter writer = new PrintWriter("ip-to-as.txt", "UTF-8");

        HashSet<String> resolvedIps = new HashSet<String>();

        int nullCount = 0;
        int reservedCount = 0;
        int internalCount = 0;

        BufferedReader br = new BufferedReader(new FileReader(bot_ips_file));
        String line;
        while ((line = br.readLine()) != null) {
            // System.out.println(line.toString());
            String ip = line.trim();
            if (!resolvedIps.contains(ip)) {
                int as = self.resolveAS(ip);
                resolvedIps.add(ip);
                writer.println(ip + " " + as);

                if (as == 0) {
                    nullCount++;
                } else {
                    if (as == FIB.RESERVED_INT) {
                        reservedCount++;
                    }
                    if (as == FIB.INTERNAL_INT) {
                        internalCount++;
                    }
                }
            }
        }
        br.close();
        writer.close();

        System.out.println("Null count: " + nullCount);
        System.out.println("Reserved count: " + reservedCount);
        System.out.println("Internal count: " + internalCount);
        System.out.println("Correctly parsed count: " + (resolvedIps.size() - nullCount - reservedCount - internalCount));
    }

    public static void cashMain(String args[]) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: cmd <origin_as_mapping.txt> <traces dir>");
            return;
        }

        FIB self = new FIB(args[0]);
        File folder = new File(args[1]);

        File[] files = folder.listFiles();

        PrintWriter writer = new PrintWriter("ip-to-as.txt", "UTF-8");

        HashSet<String> resolvedIps = new HashSet<String>();

        int nullCount = 0;
        int reservedCount = 0;
        int internalCount = 0;

        for (int i = 0; i < files.length; i++) {
            if (files[i].isFile()) {
                System.out.println(files[i].getName());
                BufferedReader br = new BufferedReader(new FileReader(args[1] + "/" + files[i].getName()));
                String line;
                while ((line = br.readLine()) != null) {
                    String[] parts = line.split(" ");
                    if (parts.length > 1) {
                        String ip = parts[1];
                        if (!resolvedIps.contains(ip)) {
                            int as = self.resolveAS(ip);
                            resolvedIps.add(ip);
                            writer.println(ip + " " + as);

                            if (as == 0) {
                                nullCount++;
                            } else {
                                if (as == FIB.RESERVED_INT) {
                                    reservedCount++;
                                }
                                if (as == FIB.INTERNAL_INT) {
                                    internalCount++;
                                }
                            }
                        }
                    }
                }
                br.close();
            }
        }

        writer.close();

        System.out.println("Null count: " + nullCount);
        System.out.println("Reserved count: " + reservedCount);
        System.out.println("Internal count: " + internalCount);
        System.out.println("Correctly parsed count: " + (resolvedIps.size() - nullCount - reservedCount - internalCount));
    }
}
