package chaos.topo;

import chaos.sim.Chaos;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * Class made up of static methods used to build the topology used by the
 * nightwing simulator.
 *
 * @author pendgaft
 */
public class ASTopoParser {

    public static void main(String args[]) throws IOException {
        /*
         * This is no more, moved to BGPMaster at this point
         */
    }

    /**
     * Static method that builds AS objects along with the number of IP
     * addresses they have. This function does NO PRUNING of the topology.
     *
     * @return - an unpruned mapping between ASN and AS objects
     * @throws IOException - if there is an issue reading any config file
     */
    public static TIntObjectMap<AS> doNetworkBuild(Chaos sim, String wardenFile,
                                                        AS.AvoidMode avoidMode,
                                                        AS.ReversePoisonMode poisonMode) throws IOException {

        TIntObjectMap<AS> asMap = ASTopoParser.parseFile(sim, sim.getConfig().asRelFile,
                wardenFile, sim.getConfig().superASFile, avoidMode, poisonMode);
        System.out.println("Raw topo size is: " + asMap.size());
        ASTopoParser.parseIPScoreFile(sim, asMap, sim.getConfig().ipCountFile);
        ASTopoParser.parseSingleTrafficWeightFile(sim, asMap, sim.getConfig().trafficModelFile);
        return asMap;
    }

    /**
     * Static method that builds AS objects along with the number of IP
     * addresses they have. This function does NO PRUNING of the topology.
     *
     * @return - an unpruned mapping between ASN and AS objects
     * @throws IOException - if there is an issue reading any config file
     */
    public static TIntObjectMap<AS> doNetworkBuild(Chaos sim) throws IOException {

        TIntObjectMap<AS> asMap = ASTopoParser.parseFile(sim, sim.getConfig().asRelFile);
        System.out.println("Raw topo size is: " + asMap.size());
        ASTopoParser.parseIPScoreFile(sim, asMap, sim.getConfig().ipCountFile);
        ASTopoParser.parseSingleTrafficWeightFile(sim, asMap, sim.getConfig().trafficModelFile);
        return asMap;
    }

    /**
     * Simple static call to do the network prune. This servers as a single
     * entry point to prune, allowing changes to the pruning strategy.
     *
     * @param workingMap - the unpruned AS map, this will be altered as a side effect
     *                   of this call
     * @return - a mapping between ASN and AS object of PRUNED ASes
     */
    public static TIntObjectMap<AS> doNetworkPrune(Chaos sim, TIntObjectMap<AS> workingMap, boolean
            largeMemEnv) {
        return ASTopoParser.pruneNoCustomerAS(sim, workingMap, largeMemEnv);
    }

    /**
     * Static method that parses the CAIDA as relationship files and the file
     * that contains ASNs that make up the warden.
     *
     * @param asRelFile - CAIDA style AS relationship file
     * @return - an unpruned mapping between ASN and AS objects
     * @throws IOException - if there is an issue reading either config file
     */
    public static TIntObjectMap<AS> parseFile(Chaos sim, String asRelFile, String wardenFile,
                                                   String superASFile,
                                                   AS.AvoidMode avoidMode,
                                                   AS.ReversePoisonMode poisonMode) throws IOException {
        String pollString;
        BufferedReader fBuff;

        TIntObjectMap<AS> retMap;
        System.out.println(asRelFile);

        retMap = ASTopoParser.buildASMap(sim, asRelFile);

        /*
         * A little bit of short circuit logic bolted on so outside
         * classes/projects can use this and not need to re-do code
         */
        if (wardenFile == null && superASFile == null) {
            return retMap;
        }

        /*
         * read the warden AS file, toggle all warden ASes
         */
        fBuff = new BufferedReader(new FileReader(wardenFile));
        Set<AS> wardenSet = new HashSet<AS>();
        int lostWardens = 0;
        while (fBuff.ready()) {
            pollString = fBuff.readLine().trim();
            if (pollString.length() > 0) {
                int asn = Integer.parseInt(pollString);

                /*
                 * Sanity check that the warden AS actually exists in the topo
                 * and flag it, the warden AS not existing is kinda bad
                 */
                if (retMap.get(asn) != null) {
                    retMap.get(asn).toggleWardenAS(avoidMode, poisonMode);
                    wardenSet.add(retMap.get(asn));
                } else {
                    lostWardens++;
                }
            }
        }

        fBuff.close();

        System.out.println(lostWardens + " listed warden ASes failed to exist in the topology.");

        /*
         * Give all nodes a copy of the warden set
         */
        for (AS tAS : retMap.valueCollection()) {
            tAS.setWardenSet(wardenSet);
        }

        /*
         * read the super AS file, toggle all super ASes
         */
        fBuff = new BufferedReader(new FileReader(superASFile));
        while (fBuff.ready()) {
            pollString = fBuff.readLine().trim();
            if (pollString.length() == 0) {
                continue;
            }

            /*
             * Ignore comments
             */
            if (pollString.charAt(0) == '#') {
                continue;
            }
            int asn = Integer.parseInt(pollString);
            System.out.println("superAS: " + asn);
            retMap.get(asn).toggleSuperAS();
        }
        fBuff.close();

        return retMap;
    }

    /**
     * Static method that parses the CAIDA as relationship files and the file
     * that contains ASNs that make up the warden.
     *
     * @param asRelFile - CAIDA style AS relationship file
     * @return - an unpruned mapping between ASN and AS objects
     * @throws IOException - if there is an issue reading either config file
     */
    public static TIntObjectMap<AS> parseFile(Chaos sim, String asRelFile) throws IOException {

        TIntObjectMap<AS> retMap;
        System.out.println(asRelFile);
        retMap = ASTopoParser.buildASMap(sim, asRelFile);

        return retMap;
    }

    private static TIntObjectMap<AS> buildASMap(Chaos sim, String asRelFile) throws IOException {
        TIntObjectMap<AS> retMap = new TIntObjectHashMap<>();

        String pollString;
        StringTokenizer pollToks;
        int lhsASN, rhsASN, rel;
        int lostASNs = 0;

        BufferedReader fBuff = null;
        try {
            fBuff = new BufferedReader(new FileReader(asRelFile));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        while (fBuff.ready()) {
            pollString = fBuff.readLine().trim();
            if (sim.getConfig().debug) {
                System.out.println(pollString);
            }

            /*
             * ignore blanks
             */
            if (pollString.length() == 0) {
                continue;
            }

            /*
             * Ignore comments
             */
            if (pollString.charAt(0) == '#') {
                continue;
            }

            /*
             * Parse line
             */
            pollToks = new StringTokenizer(pollString, "|");
            try {
                lhsASN = Integer.parseInt(pollToks.nextToken());
                rhsASN = Integer.parseInt(pollToks.nextToken());
            } catch (NumberFormatException e) {
                lostASNs++;
                continue;
            }
            rel = Integer.parseInt(pollToks.nextToken());

            /*
             * Create either AS object if we've never encountered it before
             */

            if (!retMap.containsKey(lhsASN)) {
                AS lhsAS = new AS(sim, lhsASN);
                retMap.put(lhsASN, lhsAS);
            }
            if (!retMap.containsKey(rhsASN)) {
                AS rhsAS = new AS(sim, rhsASN);
                retMap.put(rhsASN, rhsAS);
            }

            retMap.get(lhsASN).addRelation(retMap.get(rhsASN), rel);
        }
        fBuff.close();

        sim.logMessage(String.format("Lost %d ASes to being too large to fit into an int", lostASNs));

        return retMap;
    }


    /**
     * Static method to parse the IP "score" (count) file (CSV), and add that
     * attribute to the AS object
     *
     * @param asMap - the built as map (unpruned)
     * @throws IOException - if there is an issue reading from the IP count file
     */
    public static void parseIPScoreFile(Chaos sim, TIntObjectMap<AS> asMap, String ipCountFile) throws
            IOException {
        BufferedReader fBuff = new BufferedReader(new FileReader(ipCountFile));
        int unMatchedAS = 0;
        long lostIPs = 0;
        int lostASNs = 0;
        while (fBuff.ready()) {
            String pollString = fBuff.readLine().trim();
            if (pollString.length() == 0 || pollString.charAt(0) == '#') {
                continue;
            }
            StringTokenizer tokenizerTokens = new StringTokenizer(pollString, " ");
            int tAS;
            try {
                tAS = Integer.parseInt(tokenizerTokens.nextToken());
            } catch (NumberFormatException e) {
                lostASNs++;
                continue;
            }
            int score = Integer.parseInt(tokenizerTokens.nextToken());

            /*
             * Sanity check that the AS actually is in the topo and ad the IPs,
             * in theory this should never happen
             */
            if (asMap.containsKey(tAS)) {
                asMap.get(tAS).setIPCount(score);
            } else {
                unMatchedAS++;
                lostIPs += score;
            }
        }
        fBuff.close();

        sim.logMessage(unMatchedAS + " times AS records were not found in IP seeding.");
        sim.logMessage(lostIPs + " IP addresses lost as a result.");
        sim.logMessage(String.format("Lost %d ASes to being too large to fit into an int", lostASNs));

    }

    private static void parseSingleTrafficWeightFile(Chaos sim, TIntObjectMap<AS> asMap, String trafficWeightsFile)
            throws IOException {
        BufferedReader inBuff = new BufferedReader(new FileReader(trafficWeightsFile));
        int marked = 0;
        int lost = 0;
        int lostASNs = 0;
        while (inBuff.ready()) {
            String pollString = inBuff.readLine().trim();
            if (pollString.length() == 0) {
                continue;
            }

            StringTokenizer tokens = new StringTokenizer(pollString, ",");
            int tASN;
            try {
                tASN = Integer.parseInt(tokens.nextToken());
            } catch (NumberFormatException e) {
                lostASNs++;
                continue;
            }
            double trafficFactor = Double.parseDouble(tokens.nextToken());
            if (asMap.containsKey(tASN)) {
                asMap.get(tASN).setTrafficFactor(trafficFactor);
                marked++;
            } else {
                lost++;
            }
        }
        inBuff.close();

        sim.logMessage("No traffic factor ASes: " + (asMap.size() - marked) + "("
                + ((float) marked / (float) asMap.size()) + " good)");
        sim.logMessage("Lost traffic factors: " + lost);
        sim.logMessage(String.format("Lost %d ASes to being too large to fit into an int", lostASNs));
    }


    private static void parseUpDownBaseTrafficWeightFile(TIntObjectMap<AS> asMap, String trafficWeightsFile)
            throws IOException {
        BufferedReader inBuff = new BufferedReader(new FileReader(trafficWeightsFile));
        int marked = 0;
        int lost = 0;
        while (inBuff.ready()) {
            String pollString = inBuff.readLine().trim();
            if (pollString.length() == 0) {
                continue;
            }

            StringTokenizer tokens = new StringTokenizer(pollString, ",");
            int tASN = Integer.parseInt(tokens.nextToken());
            double upFrac = Double.parseDouble(tokens.nextToken());
            double downFrac = Double.parseDouble(tokens.nextToken());
            double baseFrac = Double.parseDouble(tokens.nextToken());
            if (asMap.containsKey(tASN)) {
                asMap.get(tASN).setTrafficFactors(upFrac, downFrac, baseFrac);
                marked++;
            } else {
                lost++;
            }
        }
        inBuff.close();

        System.out.println("No traffic factor ASes: " + (asMap.size() - marked) + "("
                + ((float) marked / (float) asMap.size()) + " good)");
        System.out.println("Lost traffic factors: " + lost);
    }

    public static void validateRelexive(HashMap<Integer, AS> asMap) {
        for (AS tAS : asMap.values()) {
            for (AS tCust : tAS.getCustomers()) {
                if (!tCust.getProviders().contains(tAS)) {
                    System.out.println("fail - cust");
                }
            }
            for (AS tProv : tAS.getProviders()) {
                if (!tProv.getCustomers().contains(tAS)) {
                    System.out.println("fail - prov");
                }
            }
            for (AS tPeer : tAS.getPeers()) {
                if (!tPeer.getPeers().contains(tAS)) {
                    System.out.println("fail - peer");
                }
            }
        }
    }

    /**
     * Static method that prunes out all ASes that have no customer ASes. In
     * otherwords their customer cone is only themselves. This will alter the
     * supplied AS mapping, reducing it in size and altering the AS objects
     *
     * @param asMap - the unpruned AS map, will be altered as a side effect
     * @return - a mapping of ASN to AS object containing the PRUNED AS objects
     */
    private static TIntObjectMap<AS> pruneNoCustomerAS(Chaos sim, TIntObjectMap<AS> asMap, boolean
            largeMemEnv) {
        TIntObjectMap<AS> purgeMap = null;

        TIntObjectMap<AS> testAgressiveMap = new TIntObjectHashMap<AS>();
        TIntObjectMap<AS> testNonAgressiveMap = new TIntObjectHashMap<AS>();

        /*
         * Find the ASes w/o customers
         */
        for (AS tAS : asMap.valueCollection()) {
            /*
             * Leave the super ASes in the topology as well for an efficency
             * gain
             */
            if (tAS.isSuperAS()) {
                continue;
            }

            /*
             * leave the all warden ASes connected to our topo
             */
            if (tAS.isWardenAS()) {
                continue;
            }

            /*
             * Add to the correct (agressive prune vs non-agressive prune) maps
             */
            if (tAS.getNonPrunedCustomerCount() == 0) {
                testAgressiveMap.put(tAS.getASN(), tAS);

                if (!tAS.connectedToWarden()) {
                    testNonAgressiveMap.put(tAS.getASN(), tAS);
                }
            }
        }

        /*
         * Choose which we want, agressive or non-agressive, based on resulting
         * size of active topo
         */
        if (asMap.size() - testNonAgressiveMap.size() <= sim.getConfig().maxNonAggressiveTopology) {
            System.out.println("Selected NON-AGRESSIVE prune.");
            purgeMap = testNonAgressiveMap;
        } else if (largeMemEnv || asMap.size() - testAgressiveMap.size() <= sim.getConfig().maxTopologySize) {
            System.out.println("Selected AGRESSIVE prune.");
            purgeMap = testAgressiveMap;
        } else {
            throw new RuntimeException("No topology small enough! (" + (asMap.size() - testNonAgressiveMap.size())
                    + " agresssive prune size)");
        }

        /*
         * Remove these guys from the asn map and remove them from their peer's
         * data structure
         */
        for (AS tAS : purgeMap.valueCollection()) {
            asMap.remove(tAS.getASN());
            tAS.purgeRelations();
        }

        return purgeMap;
    }
}
