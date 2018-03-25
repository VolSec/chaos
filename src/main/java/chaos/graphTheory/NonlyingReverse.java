package chaos.graphTheory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NonlyingReverse extends LyingReverse {

    private static final boolean DEBUG = false;
    private HashMap<String, List<String>> localHolepunchDecisions;

    public NonlyingReverse(CompositeGraph theTopology, HashSet<String> resistors, HashSet<String> deployers) {
        super(theTopology, resistors, deployers);
        this.localHolepunchDecisions = new HashMap<String, List<String>>();
    }

    public HashMap<String, Long> runFullTopo() {
        HashMap<String, Long> returnMap = new HashMap<String, Long>();

        boolean oneRunDone = false;

        System.out.println("ASes to do " + this.resistors.size());
        for (String tResistor : this.resistors) {
            if (DEBUG && oneRunDone) {
                break;
            }
            this.topo.resetValues(0);
            long score = this.runSingleNode(tResistor + "-provider");
            returnMap.put(tResistor, score);
            oneRunDone = true;
        }

        return returnMap;
    }

    public HashMap<String, Long> runBaseCase() {
        HashMap<String, Long> returnMap = new HashMap<String, Long>();

        for (String tResistor : this.resistors) {
            this.topo.resetValues(0);
            for (CompositeNode<Integer> tAdj : this.topo.getNodeByName(tResistor + "-provider").getConnectedNodes()) {
                this.bfs(tResistor + "-provider", tAdj.getName(), 1);
            }

            if (WEIGHTED) {
                returnMap.put(tResistor, this.topo.getWeightOf(1));
            } else {
                returnMap.put(tResistor, (long) this.topo.getNodesPartOf(1).size());
            }
        }

        return returnMap;
    }

    public HashMap<Integer, Set<Integer>> extractChoices() {
        Pattern digitPattern = Pattern.compile("\\d+");
        HashMap<Integer, Set<Integer>> retMap = new HashMap<Integer, Set<Integer>>();

        for (String tAS : this.localHolepunchDecisions.keySet()) {
            HashSet<Integer> advSet = new HashSet<Integer>();
            Matcher tMatch = digitPattern.matcher(tAS);
            tMatch.find();
            int baseAS = Integer.parseInt(tMatch.group(0));
            for (String advInstruction : this.localHolepunchDecisions.get(tAS)) {
                tMatch = digitPattern.matcher(advInstruction);
                tMatch.find();
                advSet.add(Integer.parseInt(tMatch.group(0)));
            }
            retMap.put(baseAS, advSet);
        }

        return retMap;
    }

    public long runSingleNode(String startingPoint) {

        //TODO do something wtih choices?
        List<String> choices = new LinkedList<String>();
        String choice = null;
        while ((choice = this.findBestChoice(startingPoint)) != null) {
            choices.add(choice);
            this.bfs(startingPoint, choice, 1);
        }

        this.localHolepunchDecisions.put(startingPoint, choices);
        if (WEIGHTED) {
            return this.topo.getWeightOf(1);
        } else {
            return this.topo.getNodesPartOf(1).size();
        }
    }

    private String findBestChoice(String startingPoint) {

        long currentScore = 0;
        if (WEIGHTED) {
            currentScore = this.topo.getWeightOf(1);
        } else {
            currentScore = this.topo.getNodesPartOf(1).size();
        }

        this.topo.updateSnapshot();

        String best = null;
        long bestScore = currentScore;
        HashSet<String> possibleAdv = new HashSet<String>();
        for (CompositeNode<Integer> tAdj : this.topo.getNodeByName(startingPoint).getConnectedNodes()) {
            if (tAdj.getData() > 0 || this.deployers.contains(tAdj.getCompositeName())) {
                continue;
            }
            possibleAdv.add(tAdj.getName());
        }

        if (DEBUG) {
            System.out.println("poss adv " + possibleAdv.size());
        }

        for (String tDest : possibleAdv) {
            long tempScore = this.bfs(startingPoint, tDest, 1);
            if (tempScore > bestScore) {
                bestScore = tempScore;
                best = tDest;
            }
            this.topo.revertToSnapshot();
        }

        if (DEBUG) {
            System.out.println("best is " + best);
            System.out.println("score is " + bestScore);
        }
        return best;
    }

    private long bfs(String startNode, String destNode, int spreadValue) {
        HashSet<String> visited = new HashSet<String>();
        HashSet<String> nextHorizon = new HashSet<String>();
        HashSet<String> currentHorizon = new HashSet<String>();

        visited.addAll(this.topo.getSubNodesPartOf(2));
        if (spreadValue == 1) {
            visited.addAll(this.topo.getSubNodesPartOf(1));
        }

        visited.add(startNode);
        this.topo.getNodeByName(startNode).setData(spreadValue);
        if (destNode == null) {
            CompositeNode<Integer> tNode = this.topo.getNodeByName(startNode);
            for (CompositeNode<Integer> tAdj : tNode.getConnectedNodes()) {
                currentHorizon.add(tAdj.getName());
                this.topo.getNodeByName(tAdj.getName()).setData(spreadValue);
            }
        } else {
            currentHorizon.add(destNode);
        }

        /*
         * BFS like a BALLLLLA
         */
        while (currentHorizon.size() > 0) {
            for (String tASName : currentHorizon) {
                CompositeNode<Integer> tNode = this.topo.getNodeByName(tASName);
                if (!visited.contains(tASName)) {

                    /*
                     * Deal with the case that he got marked by a recursive BFS
                     */
                    if (tNode.getData() >= spreadValue) {
                        visited.add(tNode.getName());
                        continue;
                    }

                    /*
                     * If we're spreading 1, and hit a deployer, stop and spread
                     * 2
                     */
                    if (spreadValue != 2 && this.deployers.contains(tNode.getCompositeName())) {
                        this.bfs(tNode.getName(), null, 2);
                    } else {

                        for (CompositeNode<Integer> neighbor : tNode.getConnectedNodes()) {
                            nextHorizon.add(neighbor.getName());
                        }
                        tNode.setData(spreadValue);
                    }
                }
            }

            currentHorizon.clear();
            currentHorizon.addAll(nextHorizon);
            nextHorizon.clear();
        }

        if (WEIGHTED) {
            return this.topo.getWeightOf(1);
        } else {
            return this.topo.getNodesPartOf(1).size();
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {
        //TODO change second arg to IP file when we're there

        HashSet<String> resistorSet = parseASFile("/scratch/minerva/schuch/cash-nightwing/isp-as.txt");
        List<HashSet<String>> deployerSet = parseMultiroundASFile("/scratch/waterhouse/schuch/workspace/cash-nightwing/deploys/isp.txt");
        BufferedWriter out = new BufferedWriter(new FileWriter(
                "/scratch/waterhouse/schuch/workspace/cash-nightwing/isp-nolie.txt"));

        for (HashSet<String> deploys : deployerSet) {
            System.out.println("Starting " + deploys.size());
            CompositeGraph internetTopo = SimpleAS.convertToCompositeGraph(SimpleAS.parseFromFile(
                    "/scratch/minerva/schuch/cash-nightwing/as-rel.txt",
                    "/scratch/minerva/schuch/cash-nightwing/ip-count.csv"));
            NonlyingReverse self = new NonlyingReverse(internetTopo, resistorSet, deploys);

            HashMap<String, Long> results = self.runFullTopo();
            //HashMap<String, Long> results = self.runBaseCase();

            long result = 0;
            for (String tAS : results.keySet()) {
                result += results.get(tAS) * internetTopo.getComponentWeight(tAS);
            }
            out.write("" + deploys.size() + "," + result + "\n");
            System.out.println("Finished " + deploys.size());
        }
        out.close();

    }

}
