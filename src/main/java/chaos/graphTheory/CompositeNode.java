package chaos.graphTheory;

import java.util.HashSet;
import java.util.Set;

public class CompositeNode<E> {

    private String name;
    private String compositeName;
    private E data;
    private long weight;

    private Set<CompositeNode<E>> neighbors;


    public CompositeNode(String myName, String myParentName, long nodeWeight) {
        this.name = myName;
        this.compositeName = myParentName;
        this.neighbors = new HashSet<CompositeNode<E>>();
        this.data = null;
        this.weight = nodeWeight;
    }

    public long getNodeWeight() {
        return this.weight;
    }

    public void connectToNeighbor(CompositeNode<E> newNeighbor) {
        this.neighbors.add(newNeighbor);
    }

    public void setData(E newDataValue) {
        this.data = newDataValue;
    }

    public E getData() {
        return this.data;
    }

    public String getName() {
        return this.name;
    }

    public String getCompositeName() {
        return this.compositeName;
    }

    public Set<CompositeNode<E>> getConnectedNodes() {
        return this.neighbors;
    }

    public int hashCode() {
        return this.name.hashCode();
    }

    public boolean equals(Object rhs) {
        if (!(rhs instanceof CompositeNode<?>)) {
            return false;
        }

        CompositeNode<E> nodeRhs = (CompositeNode<E>) rhs;
        return this.name.equals(nodeRhs.name);
    }
}
