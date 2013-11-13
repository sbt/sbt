// Copyright © 2011-2013, Esko Luontola <www.orfjackal.net>
// This software is released under the Apache License 2.0.
// The license text is at http://www.apache.org/licenses/LICENSE-2.0

package sbt;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public class OutputCapturer {

    // PrintStream does not do any buffering of its own; only the OutputStream that it delegates to may buffer.

    private final OutCapturer outCapturer = new OutCapturer();
    private final ErrCapturer errCapturer = new ErrCapturer();
    private final PrintStream out;
    private final PrintStream err;

    public OutputCapturer() {
        this(new NullOutputStream(), new NullOutputStream(), StandardCharsets.UTF_16BE);
    }

    public OutputCapturer(OutputStream realOut, OutputStream realErr, Charset charset) {
        OutputStream capturedOut = new WriterOutputStream(outCapturer, charset);
        OutputStream capturedErr = new WriterOutputStream(errCapturer, charset);
        err = createNonSynchronizedPrintStream(new OutputStreamReplicator(realErr, capturedErr), charset);
        out = SynchronizedPrintStream.create(new OutputStreamReplicator(realOut, capturedOut), charset, err);
    }

    private static PrintStream createNonSynchronizedPrintStream(OutputStream out, Charset charset) {
        try {
            return new PrintStream(out, false, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public PrintStream out() {
        return out;
    }

    public PrintStream err() {
        return err;
    }

	/** capture the output of the current thread */
    public void captureTo(OutputListener listener) {
        outCapturer.setListener(listener);
        errCapturer.setListener(listener);
    }


    private static class OutCapturer extends AbstractCapturer {
        @Override
        public void write(String text) {
            listener.get().out(text);
        }
    }

    private static class ErrCapturer extends AbstractCapturer {
        @Override
        public void write(String text) {
            listener.get().err(text);
        }
    }

    private static abstract class AbstractCapturer extends Writer {
        protected final ThreadLocal<OutputListener> listener = new InitializedInheritableThreadLocal<OutputListener>(new NullOutputListener());

		/** set the listener for the current thread */
        public void setListener(OutputListener listener) {
            this.listener.set(listener);
        }

        @Override
        public void write(char[] cbuf, int off, int len) {
            write(new String(cbuf, off, len));
        }

        @Override
        public abstract void write(String text);

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }

    private static class OutputStreamReplicator extends OutputStream {
        private final OutputStream out1;
        private final OutputStream out2;

        public OutputStreamReplicator(OutputStream out1, OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(byte[] bytes, int off, int len) throws IOException {
            out1.write(bytes, off, len);
            out1.flush();
            out2.write(bytes, off, len);
            out2.flush();
        }

        @Override
        public void write(int b) throws IOException {
            out1.write(b);
            out1.flush();
            out2.write(b);
            out2.flush();
        }
    }
}
