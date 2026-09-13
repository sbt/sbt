/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 * java.nio.channels.Channels.newInputStream/newOutputStream both synchronize on the channel's
 * blockingLock() for the duration of each blocking call, so a thread parked in a blocking read
 * holds that lock for as long as the read blocks, and a concurrent writer on the same channel can
 * never acquire it. These factories talk to the channel directly instead, so a SocketChannel can
 * safely be read and written from different threads at the same time.
 */
public final class DuplexChannels {
  private DuplexChannels() {}

  public static OutputStream newOutputStream(SocketChannel ch) {
    return new OutputStream() {
      @Override
      public void write(int b) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(new byte[] {(byte) b});
        while (bb.hasRemaining()) ch.write(bb);
      }

      @Override
      public void write(byte[] b, int off, int len) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b, off, len);
        while (bb.hasRemaining()) ch.write(bb);
      }
    };
  }

  public static InputStream newInputStream(SocketChannel ch) {
    return new InputStream() {
      @Override
      public int read() throws IOException {
        ByteBuffer bb = ByteBuffer.allocate(1);
        int n = ch.read(bb);
        return n <= 0 ? -1 : (bb.get(0) & 0xff);
      }

      @Override
      public int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) return 0;
        return ch.read(ByteBuffer.wrap(b, off, len));
      }
    };
  }

  /**
   * Wraps a connected SocketChannel as a Socket backed by {@link #newInputStream}/{@link
   * #newOutputStream}.
   */
  public static Socket newSocket(SocketChannel ch) {
    return new Socket() {
      private final InputStream in = newInputStream(ch);
      private final OutputStream out = newOutputStream(ch);

      @Override
      public InputStream getInputStream() {
        return in;
      }

      @Override
      public OutputStream getOutputStream() {
        return out;
      }

      @Override
      public void close() throws IOException {
        ch.close();
      }

      @Override
      public boolean isClosed() {
        return !ch.isOpen();
      }
    };
  }
}
