package chaos.sim;

import chaos.topo.AS;
import chaos.topo.BGPPath;
import chaos.util.Stats;

import java.io.IOException;
import java.util.*;

public class PathAsym {

    private HashMap<Integer, AS> activeMap;
    private HashMap<Integer, AS> purgedMap;
    private HashSet<AS> chinaAS;

    private static final String LOG_DIR = "logs/";

    public PathAsym(HashMap<Integer, AS> activeMap, HashMap<Integer, AS> purgedMap) {
        super();
        this.activeMap = activeMap;
        this.purgedMap = purgedMap;

        this.chinaAS = new HashSet<AS>();
        for (AS tAS : this.activeMap.values()) {
            if (tAS.isWardenAS()) {
                chinaAS.add(tAS);
            }
        }
    }

    public void buildPathSymCDF() {
        List<Double> asymList = new ArrayList<Double>();

        for (AS tAS : this.activeMap.values()) {
            int seen = 0;
            int asym = 0;

            for (AS tDest : this.activeMap.values()) {
                if (tDest.getASN() == tAS.getASN()) {
                    continue;
                }

                seen++;
                if (this.isAsym(tAS, tDest)) {
                    asym++;
                }
            }
            for (AS tDest : this.purgedMap.values()) {
                seen++;
                if (this.isAsym(tAS, tDest)) {
                    asym++;
                }
            }

            asymList.add((double) asym / (double) seen);
        }
        for (AS tAS : this.purgedMap.values()) {
            int seen = 0;
            int asym = 0;

            for (AS tDest : this.activeMap.values()) {
                seen++;
                if (this.isAsym(tAS, tDest)) {
                    asym++;
                }
            }
            for (AS tDest : this.purgedMap.values()) {
                if (tDest.getASN() == tAS.getASN()) {
                    continue;
                }

                seen++;
                if (this.isAsym(tAS, tDest)) {
                    asym++;
                }
            }

            asymList.add((double) asym / (double) seen);
        }

        try {
            Stats.printCDF(Collections.singletonList(asymList), PathAsym.LOG_DIR + "asym.csv");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private boolean isAsym(AS outAS, AS destAS) {
        BGPPath outPath = outAS.getPath(destAS.getASN());
        BGPPath inPath = destAS.getPath(outAS.getASN());

        if (outPath == null || inPath == null) {
            return false;
        }

        return !(outPath.equals(inPath));
    }

}
