package sbt.internal;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;

abstract class ServerSocketWrapper {
  private ServerSocketWrapper() {}

  abstract void setSoTimeout(int timeout) throws SocketException;

  abstract SocketWrapper accept() throws IOException;

  abstract void close() throws IOException;

  static ServerSocketWrapper fromServerSocket(final ServerSocket socket) {
    return new ServerSocketImpl(socket);
  }

  static ServerSocketWrapper fromServerSocketChannel(final ServerSocketChannel channel) {
    return new ServerSocketChannelImpl(channel);
  }

  private static final class ServerSocketImpl extends ServerSocketWrapper {
    private final ServerSocket socket;

    ServerSocketImpl(ServerSocket socket) {
      this.socket = socket;
    }

    @Override
    void setSoTimeout(int timeout) throws SocketException {
      socket.setSoTimeout(timeout);
    }

    @Override
    SocketWrapper accept() throws IOException {
      return SocketWrapper.fromSocket(socket.accept());
    }

    @Override
    void close() throws IOException {
      socket.close();
    }
  }

  private static final class ServerSocketChannelImpl extends ServerSocketWrapper {
    private final ServerSocketChannel channel;

    ServerSocketChannelImpl(ServerSocketChannel channel) {
      this.channel = channel;
    }

    @Override
    void setSoTimeout(int timeout) throws SocketException {}

    @Override
    SocketWrapper accept() throws IOException {
      return SocketWrapper.fromByteChannel(channel.accept());
    }

    @Override
    void close() throws IOException {
      channel.close();
    }
  }
}
