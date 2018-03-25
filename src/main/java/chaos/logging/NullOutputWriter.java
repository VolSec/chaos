package chaos.logging;

import java.io.IOException;
import java.io.Writer;

public class NullOutputWriter extends Writer {

    public NullOutputWriter() {
        super();
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void flush() throws IOException {
    }

    @Override
    public void write(char[] arg0, int arg1, int arg2) throws IOException {
    }

    @Override
    public void write(String str) throws IOException {
        //does nothing
    }

}
