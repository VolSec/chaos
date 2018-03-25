package chaos.eval.nyx.src;

import chaos.sim.Chaos;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Set;

/**
 * Bookmarking Storage class for the reactive experiments.
 */
public class BookmarkInfo implements Serializable {

    private Chaos.EvalSim myMode;
    private Integer runNum;
    private Set<Set<Integer>> usedASs;

    private static final String SERIALFILE_DIRECTORY = "serial/";

    public BookmarkInfo() {
    }

    ;

    @SuppressWarnings("unchecked")
    public void loadBookmarkFromSerialFile(ObjectInputStream serialIn) throws IOException, ClassNotFoundException {
        this.runNum = serialIn.readInt();
        this.usedASs = (Set<Set<Integer>>) serialIn.readObject();
        this.myMode = (Chaos.EvalSim) serialIn.readObject();
    }

    public void saveBookmarkToSerialFile(ObjectOutputStream serialOut) {
        try {
            serialOut.writeInt(this.runNum);
            serialOut.writeObject(this.usedASs);
            serialOut.writeObject(this.myMode);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public Chaos.EvalSim getMyMode() {
        return myMode;
    }

    public Integer getRunNum() {
        return runNum;
    }

    public void setRunNum(Integer runNum) {
        this.runNum = runNum;
    }

    public Set<Set<Integer>> getUsedASs() {
        return usedASs;
    }

    public void setUsedASs(Set<Set<Integer>> usedASs) {
        this.usedASs = usedASs;
    }

}
