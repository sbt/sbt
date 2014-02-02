
package sbt;
 
import java.io.IOException;
import java.io.OutputStream;

public class NullOutputStream extends OutputStream {
    
    public static final NullOutputStream INSTANCE = new NullOutputStream();

    @Override public void write(byte[] b, int off, int len) {}

    @Override public void write(int b) {}

    @Override public void write(byte[] b) throws IOException {}
}
