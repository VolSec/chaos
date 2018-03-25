package chaos.topo;

public class ASRanker implements Comparable<ASRanker> {

    private int asn;
    private double value;

    public ASRanker(int myASN, double myValue) {
        this.asn = myASN;
        this.value = myValue;
    }

    public int getASN() {
        return this.asn;
    }

    public int compareTo(ASRanker rhs) {
        if (this.value < rhs.value) {
            return -1;
        }
        if (this.value > rhs.value) {
            return 1;
        }
        return 0;
    }

    public String toString() {
        return this.asn + "," + this.value;
    }
}
