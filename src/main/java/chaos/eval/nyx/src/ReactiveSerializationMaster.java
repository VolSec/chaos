package chaos.eval.nyx.src;

import chaos.sim.Chaos;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public class ReactiveSerializationMaster {

    private Chaos.EvalSim evalSim;

    private byte[] hashValue;

    private static final String SERIALFILE_DIRECTORY = "serial/";
    private static final String SERIALFILE_EXP_EXT = "-EXP.ser";
    private String serialFileName;


    public static void main(String args[]) {
        ReactiveSerializationMaster tester = new ReactiveSerializationMaster(null);
        System.out.println(tester.convertHashToFileName());
    }

    public ReactiveSerializationMaster(Chaos.EvalSim evalSim) {
        this.evalSim = evalSim;
        this.serialFileName = SERIALFILE_DIRECTORY + this.evalSim.toString() + SERIALFILE_EXP_EXT;
        // this.buildFileManifest();
    }

    private void buildFileManifest() {
        MessageDigest hasher = null;
        try {
            hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        // THIS IS WRONG
        // TODO: Fix
        this.addToHash(this.evalSim.toString(), hasher);

        this.hashValue = hasher.digest();
    }

    private void addToHash(String fileName, MessageDigest hashObject) {
        try {
            BufferedReader confFile = new BufferedReader(new FileReader(fileName));
            while (confFile.ready()) {
                hashObject.update(confFile.readLine().getBytes());
            }
            confFile.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }

    }

    private String convertHashToFileName() {
        Formatter format = new Formatter();
        for (byte b : this.hashValue) {
            format.format("%02x", b);
        }
        String formatResult = format.toString();
        format.close();
        return SERIALFILE_DIRECTORY + formatResult;
    }

    private String convertHashToExpFileName() {
        return this.convertHashToFileName() + ReactiveSerializationMaster.SERIALFILE_EXP_EXT;
    }

    public void serializeExpToFile(BookmarkInfo bookmarkInfo) {
        try {
            ObjectOutputStream serialOut = new ObjectOutputStream(new FileOutputStream(this.serialFileName, false));
            bookmarkInfo.saveBookmarkToSerialFile(serialOut);
            serialOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-1);
        }

    }

    public void loadExpFromFile(BookmarkInfo bookmarkInfo) {
        try {
            ObjectInputStream serialIn = new ObjectInputStream(new FileInputStream(this.serialFileName));
            bookmarkInfo.loadBookmarkFromSerialFile(serialIn);
            serialIn.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public boolean hasValidExpSerialFile() {
        String fileName = this.convertHashToExpFileName();
        File testFileObject = new File(fileName);
        return testFileObject.exists();
    }
}
