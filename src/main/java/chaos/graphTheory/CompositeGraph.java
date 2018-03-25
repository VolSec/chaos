package chaos.graphTheory;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class CompositeGraph {

    private HashMap<String, CompositeNode<Integer>> nodeMap;
    private HashSet<String> compositeNameSet;

    private HashMap<String, Integer> valuesSnapshot;

    public CompositeGraph() {
        this.nodeMap = new HashMap<String, CompositeNode<Integer>>();
        this.compositeNameSet = new HashSet<String>();
        this.valuesSnapshot = new HashMap<String, Integer>();
    }

    public void addNewNode(String name, String parentName, long weight) {
        if (this.nodeMap.containsKey(name)) {
            throw new RuntimeException("Already have a node with that name!");
        }

        this.nodeMap.put(name, new CompositeNode<Integer>(name, parentName, weight));
        this.compositeNameSet.add(parentName);
    }

    public CompositeNode<Integer> getNodeByName(String name) {
        return this.nodeMap.get(name);
    }

    public boolean containsCompositeNode(String name) {
        return this.compositeNameSet.contains(name);
    }

    public int getCompositeVertexSize() {
        return this.compositeNameSet.size();
    }

    public long getComponentWeight(String compName) {
        String provName = compName + "-peer";
        return this.nodeMap.get(provName).getNodeWeight();
    }

    public long getWeightOf(Integer testValue) {
        HashSet<String> matchingNodes = new HashSet<String>();
        HashSet<String> excludedNodes = new HashSet<String>();
        long result = 0;

        for (CompositeNode<Integer> tNode : nodeMap.values()) {
            if (tNode.getData().equals(testValue)) {
                String compName = tNode.getCompositeName();
                if (excludedNodes.contains(compName)) {
                    continue;
                }
                if (!matchingNodes.contains(compName)) {
                    matchingNodes.add(compName);
                    result += tNode.getNodeWeight();
                }
            }
            if (tNode.getData() > testValue) {
                String compName = tNode.getCompositeName();
                if (excludedNodes.contains(compName)) {
                    continue;
                }
                if (matchingNodes.contains(compName)) {
                    matchingNodes.remove(compName);
                    result -= tNode.getNodeWeight();
                }
                excludedNodes.add(compName);
            }
        }

        return result;
    }

    public Set<String> getNodesPartOf(Integer testValue) {
        HashSet<String> matchingNodes = new HashSet<String>();
        for (CompositeNode<Integer> tNode : nodeMap.values()) {
            if (tNode.getData().equals(testValue)) {
                matchingNodes.add(tNode.getCompositeName());
            }
            if (tNode.getData() > testValue) {
                matchingNodes.remove(tNode.getCompositeName());
            }
        }

        return matchingNodes;
    }

    public Set<String> getSubNodesPartOf(Integer testValue) {
        HashSet<String> matchingNodes = new HashSet<String>();
        for (CompositeNode<Integer> tNode : this.nodeMap.values()) {
            if (tNode.getData().equals(testValue)) {
                matchingNodes.add(tNode.getName());
            }
        }

        return matchingNodes;
    }

    public void resetValues(Integer resetValue) {
        for (CompositeNode<Integer> tNode : nodeMap.values()) {
            tNode.setData(resetValue);
        }
    }

    public void updateSnapshot() {
        this.valuesSnapshot.clear();

        for (String tNodeName : this.nodeMap.keySet()) {
            this.valuesSnapshot.put(tNodeName, this.nodeMap.get(tNodeName).getData());
        }
    }

    public void revertToSnapshot() {
        if (this.valuesSnapshot.size() != this.nodeMap.size()) {
            throw new RuntimeException("Error trying to revert values via snapsot, possibly not set?");
        }

        for (CompositeNode<Integer> tNode : this.nodeMap.values()) {
            tNode.setData(this.valuesSnapshot.get(tNode.getName()));
        }
    }
}
