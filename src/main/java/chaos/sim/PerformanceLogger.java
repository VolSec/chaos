package chaos.sim;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class PerformanceLogger {

    private BufferedWriter outFP;
    private long time;

    public PerformanceLogger(String dir, String filename) {
        try {
            this.outFP = new BufferedWriter(new FileWriter(dir + filename));
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }
        this.time = System.currentTimeMillis();
    }

    public void resetTimer() {
        this.time = System.currentTimeMillis();
    }

    public void logTime(String activity, Boolean inSeconds) {
        long endTime = System.currentTimeMillis();
        try {
            if (inSeconds) {
                this.outFP.write(activity + " took " + ((endTime - this.time) / 1000) + "\n");
            } else {
                this.outFP.write(activity + " took " + ((endTime - this.time) / 60000) + "\n");
            }
            this.outFP.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        this.resetTimer();
    }

    public void logTime(String activity) {
        long endTime = System.currentTimeMillis();
        try {
            this.outFP.write(activity + " took " + ((endTime - this.time) / 60000) + "\n");
            this.outFP.flush();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
        }

        this.resetTimer();
    }


    public void done() {
        try {
            this.outFP.close();
        } catch (IOException e) {
            e.printStackTrace();
            System.exit(-2);
            ;
        }
    }

}
