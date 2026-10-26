/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
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
   * Wraps a connected SocketChannel as a Socket that behaves like a plain java.net.Socket: the
   * channel is switched to non-blocking mode and each direction waits on its own Selector, so a
   * read and a write can proceed concurrently, SO_TIMEOUT bounds a read with a {@link
   * SocketTimeoutException}, and interrupting a blocked thread raises an {@link
   * InterruptedIOException} without closing the channel.
   */
  public static Socket newSocket(SocketChannel ch) throws IOException {
    Selector readSelector = null;
    Selector writeSelector = null;
    try {
      ch.configureBlocking(false);
      readSelector = Selector.open();
      writeSelector = Selector.open();
      ch.register(readSelector, SelectionKey.OP_READ);
      ch.register(writeSelector, SelectionKey.OP_WRITE);
    } catch (IOException e) {
      if (readSelector != null) readSelector.close();
      if (writeSelector != null) writeSelector.close();
      ch.close();
      throw e;
    }
    return new SelectorSocket(ch, readSelector, writeSelector);
  }

  private static final class SelectorSocket extends Socket {
    private final SocketChannel ch;
    private final Selector readSelector;
    private final Selector writeSelector;
    private volatile int timeout = 0;

    private final InputStream in =
        new InputStream() {
          @Override
          public int read() throws IOException {
            byte[] b = new byte[1];
            int n = read(b, 0, 1);
            return n < 0 ? -1 : (b[0] & 0xff);
          }

          @Override
          public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) return 0;
            return readSome(ByteBuffer.wrap(b, off, len));
          }
        };

    private final OutputStream out =
        new OutputStream() {
          @Override
          public void write(int b) throws IOException {
            write(new byte[] {(byte) b}, 0, 1);
          }

          @Override
          public void write(byte[] b, int off, int len) throws IOException {
            writeAll(ByteBuffer.wrap(b, off, len));
          }
        };

    SelectorSocket(SocketChannel ch, Selector readSelector, Selector writeSelector) {
      this.ch = ch;
      this.readSelector = readSelector;
      this.writeSelector = writeSelector;
    }

    private int readSome(ByteBuffer bb) throws IOException {
      synchronized (readSelector) {
        try {
          long deadline = timeout > 0 ? System.nanoTime() + timeout * 1_000_000L : 0L;
          while (true) {
            int n = ch.read(bb);
            if (n != 0) return n;
            long waitMs = 0L;
            if (deadline != 0L) {
              long remaining = deadline - System.nanoTime();
              if (remaining <= 0) throw new SocketTimeoutException("Read timed out");
              waitMs = Math.max(1L, remaining / 1_000_000L);
            }
            readSelector.select(waitMs);
            readSelector.selectedKeys().clear();
            checkInterrupt();
          }
        } catch (ClosedChannelException | ClosedSelectorException e) {
          throw closed(e);
        }
      }
    }

    private void writeAll(ByteBuffer bb) throws IOException {
      synchronized (writeSelector) {
        try {
          while (bb.hasRemaining()) {
            if (ch.write(bb) == 0) {
              writeSelector.select();
              writeSelector.selectedKeys().clear();
              checkInterrupt();
            }
          }
        } catch (ClosedChannelException | ClosedSelectorException e) {
          throw closed(e);
        }
      }
    }

    @Override
    public InputStream getInputStream() {
      return in;
    }

    @Override
    public OutputStream getOutputStream() {
      return out;
    }

    @Override
    public void setSoTimeout(int t) throws SocketException {
      if (t < 0) throw new IllegalArgumentException("timeout can't be negative");
      timeout = t;
    }

    @Override
    public int getSoTimeout() {
      return timeout;
    }

    @Override
    public void close() throws IOException {
      try {
        ch.close();
      } finally {
        try {
          readSelector.close();
        } finally {
          writeSelector.close();
        }
      }
    }

    @Override
    public boolean isClosed() {
      return !ch.isOpen();
    }

    @Override
    public boolean isConnected() {
      return ch.isConnected();
    }

    @Override
    public void shutdownInput() throws IOException {
      ch.shutdownInput();
      readSelector.wakeup();
    }

    @Override
    public void shutdownOutput() throws IOException {
      ch.shutdownOutput();
    }
  }

  /**
   * Wraps a bound ServerSocketChannel as a ServerSocket whose {@link ServerSocket#accept} returns
   * sockets made by {@link #newSocket}. SO_TIMEOUT bounds how long accept waits, as it does for a
   * plain ServerSocket.
   */
  public static ServerSocket newServerSocket(ServerSocketChannel ch) throws IOException {
    Selector selector = Selector.open();
    try {
      ch.configureBlocking(false);
      ch.register(selector, SelectionKey.OP_ACCEPT);
    } catch (IOException e) {
      selector.close();
      throw e;
    }
    return new ServerSocket() {
      private volatile int timeout = 0;

      @Override
      public Socket accept() throws IOException {
        try {
          while (true) {
            if (!ch.isOpen()) throw new SocketException("Socket is closed");
            int n = selector.select(timeout);
            selector.selectedKeys().clear();
            SocketChannel client = ch.accept();
            if (client != null) return newSocket(client);
            checkInterrupt();
            if (n == 0 && timeout > 0) throw new SocketTimeoutException("Accept timed out");
          }
        } catch (ClosedChannelException | ClosedSelectorException e) {
          throw closed(e);
        }
      }

      @Override
      public void setSoTimeout(int t) throws SocketException {
        if (t < 0) throw new IllegalArgumentException("timeout can't be negative");
        timeout = t;
      }

      @Override
      public int getSoTimeout() {
        return timeout;
      }

      @Override
      public void close() throws IOException {
        try {
          ch.close();
        } finally {
          selector.close();
        }
      }

      @Override
      public boolean isClosed() {
        return !ch.isOpen();
      }
    };
  }

  private static void checkInterrupt() throws InterruptedIOException {
    if (Thread.currentThread().isInterrupted()) throw new InterruptedIOException("interrupted");
  }

  private static SocketException closed(Throwable cause) {
    SocketException se = new SocketException("Socket is closed");
    se.initCause(cause);
    return se;
  }
}
