package chaos.graphTheory;

import java.io.*;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

public class LyingReverse {

    protected CompositeGraph topo;
    protected HashSet<String> resistors;
    protected HashSet<String> deployers;

    protected static final boolean WEIGHTED = true;

    public LyingReverse(CompositeGraph topology, HashSet<String> resistorASes, HashSet<String> deployerASes) {
        this.topo = topology;
        this.resistors = resistorASes;
        this.deployers = deployerASes;
        this.handlePrune(this.resistors);
        this.handlePrune(this.deployers);
    }

    private void handlePrune(HashSet<String> starting) {
        HashSet<String> toRemove = new HashSet<String>();
        for (String tCompName : starting) {
            if (!this.topo.containsCompositeNode(tCompName)) {
                toRemove.add(tCompName);
            }
        }

        starting.removeAll(toRemove);
    }

    private HashMap<String, Long> buildGraph() {
        HashMap<String, Long> results = new HashMap<String, Long>();

        for (String tResistor : this.resistors) {
            results.put(tResistor, this.bfs(tResistor));
        }

        return results;
    }

    protected long bfs(String rootNode) {
        this.resetGraph();

        HashSet<String> visited = new HashSet<String>();
        for (String tDeployer : this.deployers) {
            visited.add(tDeployer + "-customer");
            visited.add(tDeployer + "-peer");
            visited.add(tDeployer + "-provider");
        }
        HashSet<String> nextHorizon = new HashSet<String>();
        HashSet<String> currentHorizon = new HashSet<String>();

        currentHorizon.add(rootNode + "-provider");
        this.topo.getNodeByName(rootNode + "-provider").setData(1);

        /*
         * BFS like a BALLLLLA
         */
        while (currentHorizon.size() > 0) {
            for (String tASName : currentHorizon) {
                if (!visited.contains(tASName)) {
                    CompositeNode<Integer> tNode = this.topo.getNodeByName(tASName);
                    for (CompositeNode<Integer> neighbor : tNode.getConnectedNodes()) {
                        nextHorizon.add(neighbor.getName());
                    }
                    tNode.setData(1);
                }
            }

            currentHorizon.clear();
            currentHorizon.addAll(nextHorizon);
            nextHorizon.clear();
        }

        if (LyingReverse.WEIGHTED) {
            return this.topo.getWeightOf(1);
        } else {
            return this.topo.getNodesPartOf(1).size();
        }
    }

    protected void resetGraph() {
        this.topo.resetValues(0);
    }

    /**
     * @param args
     */
    public static void main(String[] args) throws IOException {

        HashSet<String> resistorSet = LyingReverse.parseASFile("/scratch/minerva/schuch/cash-nightwing/isp-as.txt");
        List<HashSet<String>> deploySets = LyingReverse
                .parseMultiroundASFile("/scratch/waterhouse/schuch/workspace/cash-nightwing/deploys/isp.txt");

        BufferedWriter outWriter = new BufferedWriter(new FileWriter(
                "/scratch/waterhouse/schuch/workspace/cash-nightwing/isp-lie.csv"));
        for (HashSet<String> deployers : deploySets) {
            CompositeGraph internetTopo = SimpleAS.convertToCompositeGraph(SimpleAS.parseFromFile(
                    "/scratch/minerva/schuch/cash-nightwing/as-rel.txt",
                    "/scratch/minerva/schuch/cash-nightwing/ip-count.csv"));
            LyingReverse self = new LyingReverse(internetTopo, resistorSet, deployers);
            HashMap<String, Long> results = self.buildGraph();

            long sum = 0;
            for (String node : results.keySet()) {
                sum += results.get(node) * internetTopo.getComponentWeight(node);
            }
            outWriter.write("" + deployers.size() + "," + sum + "\n");
        }
        outWriter.close();
    }

    public static List<HashSet<String>> parseMultiroundASFile(String fileName) throws IOException {
        BufferedReader fBuff = new BufferedReader(new FileReader(fileName));
        String line = null;
        List<HashSet<String>> returnList = new LinkedList<HashSet<String>>();

        HashSet<String> returnSet = new HashSet<String>();
        while ((line = fBuff.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0) {
                //do it just to sanity check that we're playing with numerical input
                Integer.parseInt(line);
                returnSet.add(line);
            } else {
                returnList.add(returnSet);
                returnSet = new HashSet<String>();
            }
        }
        fBuff.close();

        return returnList;

    }

    public static HashSet<String> parseASFile(String fileName) throws IOException {
        BufferedReader fBuff = new BufferedReader(new FileReader(fileName));
        String line = null;

        HashSet<String> returnSet = new HashSet<String>();
        while ((line = fBuff.readLine()) != null) {
            line = line.trim();
            if (line.length() > 0) {
                //do it just to sanity check that we're playing with numerical input
                Integer.parseInt(line);
                returnSet.add(line);
            }
        }
        fBuff.close();

        return returnSet;
    }

}
