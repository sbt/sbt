package sbt.internal;

import java.io.IOException;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.ByteChannel;

abstract class SocketWrapper {
  private SocketWrapper() {}

  abstract void write(int value) throws IOException;

  abstract void write(byte[] value) throws IOException;

  abstract void write(byte[] b, int offset, int len) throws IOException;

  abstract void close() throws IOException;

  abstract int read() throws IOException;

  abstract void flush() throws IOException;

  static SocketWrapper fromSocket(Socket socket) {
    return new SocketImpl(socket);
  }

  static SocketWrapper fromByteChannel(ByteChannel channel) {
    return new ByteChannelImpl(channel);
  }

  private static final class ByteChannelImpl extends SocketWrapper {
    private final ByteChannel channel;

    private ByteChannelImpl(ByteChannel channel) {
      this.channel = channel;
    }

    @Override
    void write(int value) throws IOException {
      channel.write(ByteBuffer.wrap(new byte[] {(byte) value}));
    }

    @Override
    void write(byte[] value) throws IOException {
      channel.write(ByteBuffer.wrap(value));
    }

    @Override
    void write(byte[] b, int offset, int len) throws IOException {
      channel.write(ByteBuffer.wrap(b, offset, len));
    }

    @Override
    void close() throws IOException {
      channel.close();
    }

    @Override
    int read() throws IOException {
      final ByteBuffer buf = ByteBuffer.allocate(1);
      int n;
      do {
        n = channel.read(buf);
      } while (n == 0);

      if (-1 == n) {
        return -1;
      } else {
        return buf.get(0) & 0xff;
      }
    }

    @Override
    void flush() {}
  }

  private static final class SocketImpl extends SocketWrapper {
    private final Socket socket;

    private SocketImpl(Socket socket) {
      this.socket = socket;
    }

    @Override
    void write(int value) throws IOException {
      socket.getOutputStream().write(value);
    }

    @Override
    void write(byte[] value) throws IOException {
      socket.getOutputStream().write(value);
    }

    @Override
    void write(byte[] b, int offset, int len) throws IOException {
      socket.getOutputStream().write(b, offset, len);
    }

    @Override
    void close() throws IOException {
      socket.getOutputStream().close();
      socket.getInputStream().close();
    }

    @Override
    int read() throws IOException {
      return socket.getInputStream().read();
    }

    @Override
    void flush() throws IOException {
      socket.getOutputStream().flush();
    }
  }
}
