package chaos.topo;

import chaos.sim.Chaos;
import gnu.trove.map.TIntObjectMap;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.List;

public class SerializationMaster {

    private Chaos sim;
    private byte[] hashValue;

    private static final String SERIALFILE_DIRECTORY = "serial/";
    private static final String SERIALFILE_BGP_EXT = "-BGP.ser";
    private static final String SERIALFILE_TRAFFIC_EXT = "-TRAFFIC.ser";

    public SerializationMaster(Chaos sim) {
        this.sim = sim;
        this.buildFileManifest();
    }

    public boolean hasValidBGPSerialFile() {
        String fileName = this.convertHashToBGPFileName();
        File testFileObject = new File(fileName);
        return testFileObject.exists();
    }

    public boolean hasValidTrafficSerialFile() {
        String fileName = this.convertHashToTrafficFileName();
        File testFileObject = new File(fileName);
        return testFileObject.exists();
    }

    private void buildFileManifest() {
        MessageDigest hasher = null;
        try {
            hasher = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        this.addToHash(this.sim.getConfig().asRelFile, hasher);
        this.addToHash(this.sim.getConfig().ipCountFile, hasher);

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
        return SerializationMaster.SERIALFILE_DIRECTORY + formatResult;
    }

    private String convertHashToBGPFileName() {
        return this.convertHashToFileName() + SerializationMaster.SERIALFILE_BGP_EXT;
    }

    private String convertHashToTrafficFileName() {
        return this.convertHashToFileName() + SerializationMaster.SERIALFILE_TRAFFIC_EXT;
    }

    public void loadBGPSerialFile(TIntObjectMap<AS> activeTopo) {
        try {
            ObjectInputStream serialIn = new ObjectInputStream(new FileInputStream(this.convertHashToBGPFileName()));
            List<Integer> sortedAS = this.buildSortedASNList(activeTopo.keys());
            for (int tASN : sortedAS) {
                activeTopo.get(tASN).loadASFromSerial(serialIn);
            }
            serialIn.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void buildBGPSerialFile(TIntObjectMap<AS> activeTopo) {
        try {
            ObjectOutputStream serialOut = new ObjectOutputStream(new FileOutputStream(this.convertHashToBGPFileName()));
            List<Integer> sortedAS = this.buildSortedASNList(activeTopo.keys());
            for (int tASN : sortedAS) {
                activeTopo.get(tASN).saveASToSerial(serialOut);
            }
            serialOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    public void loadTrafficSerialFile(TIntObjectMap<AS> fullTopo) {
        try {
            ObjectInputStream serialIn = new ObjectInputStream(new FileInputStream(this.convertHashToTrafficFileName()));
            List<Integer> sortedAS = this.buildSortedASNList(fullTopo.keys());
            for (int tASN : sortedAS) {
                fullTopo.get(tASN).loadTrafficFromSerial(serialIn);
            }
            serialIn.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public void buildTrafficSerialFile(TIntObjectMap<AS> fullTopo) {
        try {
            ObjectOutputStream serialOut = new ObjectOutputStream(new FileOutputStream(this.convertHashToTrafficFileName()));
            List<Integer> sortedAS = this.buildSortedASNList(fullTopo.keys());
            for (int tASN : sortedAS) {
                fullTopo.get(tASN).saveTrafficToSerial(serialOut);
            }
            serialOut.close();
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

    private List<Integer> buildSortedASNList(int[] activeASNs) {
        List<Integer> sortedList = new ArrayList<Integer>(activeASNs.length);
        for (int tInt : activeASNs) {
            sortedList.add(tInt);
        }
        Collections.sort(sortedList);
        return sortedList;
    }
}
