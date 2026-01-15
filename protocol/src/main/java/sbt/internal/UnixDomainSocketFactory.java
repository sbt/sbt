/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Factory for creating Unix domain sockets.
 *
 * <p>On JDK 17+, uses native java.net.UnixDomainSocketAddress (no JNI required). On older JDKs,
 * falls back to ipcsocket library (requires JNI).
 *
 * <p>This enables musl static linking on JDK 17+ by avoiding JNI dependencies. The ipcsocket
 * classes are loaded via reflection only when needed, so they won't be loaded on JDK 17+.
 */
public final class UnixDomainSocketFactory {

  private static final boolean JDK17_AVAILABLE;
  private static final Method UNIX_ADDRESS_OF_METHOD;
  private static final Object UNIX_PROTOCOL_FAMILY;

  static {
    boolean available = false;
    Method ofMethod = null;
    Object unixFamily = null;

    try {
      Class<?> unixAddressClass = Class.forName("java.net.UnixDomainSocketAddress");
      ofMethod = unixAddressClass.getMethod("of", Path.class);

      @SuppressWarnings("unchecked")
      Class<? extends Enum<?>> protocolFamilyClass =
          (Class<? extends Enum<?>>) Class.forName("java.net.StandardProtocolFamily");
      for (Object constant : protocolFamilyClass.getEnumConstants()) {
        if ("UNIX".equals(((Enum<?>) constant).name())) {
          unixFamily = constant;
          break;
        }
      }
      if (unixFamily != null) {
        available = true;
      }
    } catch (ClassNotFoundException | NoSuchMethodException | SecurityException e) {
      available = false;
    }

    JDK17_AVAILABLE = available;
    UNIX_ADDRESS_OF_METHOD = ofMethod;
    UNIX_PROTOCOL_FAMILY = unixFamily;
  }

  public static boolean isJdk17Available() {
    return JDK17_AVAILABLE;
  }

  public static Socket newSocket(String path, boolean useJni) throws IOException {
    if (JDK17_AVAILABLE && !useJni) {
      return newJdk17Socket(path);
    } else {
      return newLegacySocket(path, useJni);
    }
  }

  public static ServerSocket newServerSocket(String path, boolean useJni) throws IOException {
    if (JDK17_AVAILABLE && !useJni) {
      return newJdk17ServerSocket(path);
    } else {
      return newLegacyServerSocket(path, useJni);
    }
  }

  private static Socket newLegacySocket(String path, boolean useJni) throws IOException {
    try {
      Class<?> clazz = Class.forName("org.scalasbt.ipcsocket.UnixDomainSocket");
      return (Socket) clazz.getConstructor(String.class, boolean.class).newInstance(path, useJni);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Failed to create ipcsocket UnixDomainSocket", e);
    }
  }

  private static ServerSocket newLegacyServerSocket(String path, boolean useJni)
      throws IOException {
    try {
      Class<?> clazz = Class.forName("org.scalasbt.ipcsocket.UnixDomainServerSocket");
      return (ServerSocket)
          clazz.getConstructor(String.class, boolean.class).newInstance(path, useJni);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Failed to create ipcsocket UnixDomainServerSocket", e);
    }
  }

  private static Socket newJdk17Socket(String path) throws IOException {
    try {
      SocketAddress address = (SocketAddress) UNIX_ADDRESS_OF_METHOD.invoke(null, Paths.get(path));
      SocketChannel channel =
          (SocketChannel)
              SocketChannel.class
                  .getMethod("open", java.net.ProtocolFamily.class)
                  .invoke(null, UNIX_PROTOCOL_FAMILY);
      channel.connect(address);
      return new ChannelSocket(channel);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Failed to create JDK 17 Unix domain socket", e);
    }
  }

  private static ServerSocket newJdk17ServerSocket(String path) throws IOException {
    try {
      SocketAddress address = (SocketAddress) UNIX_ADDRESS_OF_METHOD.invoke(null, Paths.get(path));
      ServerSocketChannel channel =
          (ServerSocketChannel)
              ServerSocketChannel.class
                  .getMethod("open", java.net.ProtocolFamily.class)
                  .invoke(null, UNIX_PROTOCOL_FAMILY);
      channel.bind(address);
      return new ChannelServerSocket(channel);
    } catch (ReflectiveOperationException e) {
      throw new IOException("Failed to create JDK 17 Unix domain server socket", e);
    }
  }

  private UnixDomainSocketFactory() {}

  public static class ChannelSocket extends Socket {
    private final SocketChannel channel;
    private final InputStream inputStream;
    private final OutputStream outputStream;

    public ChannelSocket(SocketChannel channel) {
      this.channel = channel;
      this.inputStream = Channels.newInputStream(channel);
      this.outputStream = Channels.newOutputStream(channel);
    }

    @Override
    public InputStream getInputStream() {
      return inputStream;
    }

    @Override
    public OutputStream getOutputStream() {
      return outputStream;
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }

    @Override
    public boolean isClosed() {
      return !channel.isOpen();
    }

    @Override
    public boolean isConnected() {
      return channel.isConnected();
    }

    @Override
    public SocketChannel getChannel() {
      return channel;
    }
  }

  public static class ChannelServerSocket extends ServerSocket {
    private final ServerSocketChannel channel;
    private int soTimeout = 0;

    public ChannelServerSocket(ServerSocketChannel channel) throws IOException {
      this.channel = channel;
      channel.configureBlocking(true);
    }

    @Override
    public Socket accept() throws IOException {
      if (soTimeout > 0) {
        channel.configureBlocking(false);
        long deadline = System.currentTimeMillis() + soTimeout;
        while (System.currentTimeMillis() < deadline) {
          SocketChannel clientChannel = channel.accept();
          if (clientChannel != null) {
            return new ChannelSocket(clientChannel);
          }
          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new java.net.SocketTimeoutException("Accept interrupted");
          }
        }
        throw new java.net.SocketTimeoutException("Accept timed out");
      } else {
        channel.configureBlocking(true);
        SocketChannel clientChannel = channel.accept();
        return new ChannelSocket(clientChannel);
      }
    }

    @Override
    public void close() throws IOException {
      channel.close();
    }

    @Override
    public boolean isClosed() {
      return !channel.isOpen();
    }

    @Override
    public ServerSocketChannel getChannel() {
      return channel;
    }

    @Override
    public void setSoTimeout(int timeout) throws java.net.SocketException {
      this.soTimeout = timeout;
    }

    @Override
    public int getSoTimeout() throws java.net.SocketException {
      return soTimeout;
    }
  }
}
