package chaos.logging;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.concurrent.LinkedBlockingQueue;

public class ThreadedWriter extends Writer implements Runnable {

    private BufferedWriter actualOutput;
    private LinkedBlockingQueue<String> internalQueue;

    private static final String POISON_PILL = "094892489237489237589237589234289347289";

    public ThreadedWriter(String fileName) throws IOException {
        super();
        this.actualOutput = new BufferedWriter(new FileWriter(fileName));
        this.internalQueue = new LinkedBlockingQueue<String>();
    }

    @Override
    public void run() {
        String currentStr = null;

        try {
            while (true) {
                currentStr = this.internalQueue.take();
                if (currentStr.equals(ThreadedWriter.POISON_PILL)) {
                    break;
                } else {
                    this.actualOutput.write(currentStr);
                }
            }
        } catch (InterruptedException e1) {
            e1.printStackTrace();
            System.exit(-2);
        } catch (IOException e2) {
            System.err.println("HOLY FUCK IO EXCEPTION");
            e2.printStackTrace();
            System.exit(-2);
        }

        try {
            this.actualOutput.close();
        } catch (IOException e2) {
            System.err.println("HOLY FUCK IO EXCEPTION");
            e2.printStackTrace();
            System.exit(-2);
        }

        if (this.internalQueue.size() > 0) {
            throw new RuntimeException("Information added to threaded writer after close!");
        }
    }

    @Override
    public void close() throws IOException {
        try {
            this.internalQueue.put(ThreadedWriter.POISON_PILL);
        } catch (InterruptedException e) {
            e.printStackTrace();
            System.exit(-2);
        }
    }

    @Override
    public void flush() throws IOException {
        // TODO Auto-generated method stub

    }

    @Override
    public void write(char[] cbuf, int off, int len) throws IOException {
        throw new RuntimeException("Incorrect method to write with.");
    }

    public void write(String outStr) throws IOException {
        try {
            this.internalQueue.put(outStr);
        } catch (InterruptedException e) {
            throw new IOException("error inserting into internal queue");
        }
    }

}
