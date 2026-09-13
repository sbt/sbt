/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import sbt.internal.util.Terminal;
import xsbti.AppConfiguration;

/**
 * A BootServerSocket is used for remote clients to connect to sbt for io while sbt is still loading
 * the build. There are two scenarios in which this functionality is needed:
 *
 * <p>1. client a starts an sbt server and then client b tries to connect to the server before the
 * server has loaded. Presently, client b will try to start a new server even though there is one
 * booting. This can cause a java process leak because the second server launched by client b is
 * unable to create a server because there is an existing portfile by the time it starts up.
 *
 * <p>2. a remote client initiates a reboot command. Reboot causes sbt to shutdown the server which
 * makes the client disconnect. Since sbt does not start the server until the project has
 * successfully loaded, there is no way for the client to see the output of the server. This is
 * particularly problematic if loading fails because the server will be stuck waiting for input that
 * will not be forthcoming.
 *
 * <p>To address these issues, the BootServerSocket can be used to immediately create a server
 * socket before sbt even starts loading the build. It works by creating a local Unix domain socket
 * at a path under XDG_RUNTIME_DIR (or java.io.tmpdir) on all platforms, including Windows 10+ which
 * supports AF_UNIX via JDK 16+. If the server can't create a server socket because there is already
 * one running, it either prompts the user if they want to start a new server even if there is
 * already one running if there is a console available or exits with the status code 2 which
 * indicates that there is another sbt process starting up.
 *
 * <p>Once the server socket is created, it listens for new client connections. When a client
 * connects, the server will forward its input and output to the client via Terminal.setBootStreams
 * which updates the Terminal.proxyOutputStream to forward all bytes written to the
 * BootServerSocket's outputStream which in turn writes the output to each of the connected clients.
 * Input is handed similarly.
 *
 * <p>When the server finishes loading, it closes the boot server socket.
 *
 * <p>BootServerSocket is implemented in java so that it can be classloaded as quickly as possible.
 */
public class BootServerSocket implements AutoCloseable {
  private ServerSocketChannel serverChannel = null;
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicInteger threadId = new AtomicInteger(1);
  private final Future<?> acceptFuture;
  private final ExecutorService service =
      Executors.newCachedThreadPool(
          r -> new Thread(r, "boot-server-socket-thread-" + threadId.getAndIncrement()));
  private final Set<ClientSocket> clientSockets = ConcurrentHashMap.newKeySet();
  private final Object lock = new Object();
  private final LinkedBlockingQueue<ClientSocket> clientSocketReads = new LinkedBlockingQueue<>();
  private final Path socketFile;
  private final AtomicBoolean needInput = new AtomicBoolean(false);

  @SuppressWarnings("deprecation")
  private class ClientSocket implements AutoCloseable {
    private final InputStream in;
    private final OutputStream out;
    private final Closeable closeable;
    final AtomicBoolean alive = new AtomicBoolean(true);
    final Future<?> future;
    private final LinkedBlockingQueue<Integer> bytes = new LinkedBlockingQueue<Integer>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private ClientSocket(final InputStream in, final OutputStream out, final Closeable closeable) {
      this.in = in;
      this.out = out;
      this.closeable = closeable;
      clientSockets.add(this);
      Future<?> f = null;
      try {
        f =
            service.submit(
                () -> {
                  try {
                    Terminal.console()
                        .getLines()
                        .foreach(
                            l -> {
                              try {
                                write((l + System.lineSeparator()).getBytes("UTF-8"));
                              } catch (final IOException e) {
                              }
                              return 0;
                            });
                    while (alive.get()) {
                      try {
                        synchronized (needInput) {
                          while (!needInput.get() && alive.get()) needInput.wait();
                        }
                        if (alive.get()) {
                          out.write(5);
                          int b = in.read();
                          if (b != -1) {
                            bytes.put(b);
                            clientSocketReads.put(ClientSocket.this);
                          } else {
                            // close() deregisters from clientSockets like the write
                            // methods do; a dead entry left behind would block the
                            // NO_BOOT_CLIENTS_CONNECTED signal in inputStream.read.
                            alive.set(false);
                            close();
                          }
                        }

                      } catch (IOException e) {
                        alive.set(false);
                        close();
                      }
                    }
                  } catch (final Exception ex) {
                  }
                });
      } catch (final RejectedExecutionException e) {
        alive.set(false);
      }
      future = f;
    }

    private void write(final int i) {
      try {
        if (alive.get()) out.write(i);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void write(final byte[] b) {
      try {
        if (alive.get()) out.write(b);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void write(final byte[] b, final int offset, final int len) {
      try {
        if (alive.get()) out.write(b, offset, len);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void flush() {
      try {
        out.flush();
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    @SuppressWarnings("EmptyCatchBlock")
    @Override
    public void close() {
      if (closed.compareAndSet(false, true)) {
        if (alive.get()) {
          write(2);
          bytes.forEach(this::write);
          bytes.clear();
          write(3);
          flush();
        }
        alive.set(false);
        if (future != null) future.cancel(true);
        try {
          closeable.close();
        } catch (final IOException e) {
        }
        clientSockets.remove(this);
      }
    }
  }

  private final Object writeLock = new Object();

  public InputStream inputStream() {
    return inputStream;
  }

  private final InputStream inputStream =
      new InputStream() {
        @Override
        public int read() {
          if (clientSockets.isEmpty()) return Terminal.NO_BOOT_CLIENTS_CONNECTED();
          try {
            synchronized (needInput) {
              needInput.set(true);
              needInput.notifyAll();
            }
            ClientSocket clientSocket = clientSocketReads.take();
            return clientSocket.bytes.take();
          } catch (final InterruptedException e) {
            return -1;
          } finally {
            synchronized (needInput) {
              needInput.set(false);
            }
          }
        }
      };
  private final OutputStream outputStream =
      new OutputStream() {
        @Override
        public void write(final int b) {
          synchronized (lock) {
            clientSockets.forEach(cs -> cs.write(b));
          }
        }

        @Override
        public void write(final byte[] b) {
          write(b, 0, b.length);
        }

        @Override
        public void write(final byte[] b, final int offset, final int len) {
          synchronized (lock) {
            clientSockets.forEach(cs -> cs.write(b, offset, len));
          }
        }

        @Override
        public void flush() {
          synchronized (lock) {
            clientSockets.forEach(cs -> cs.flush());
          }
        }
      };

  public OutputStream outputStream() {
    return outputStream;
  }

  // Blocking accept; close() interrupts it via AsynchronousCloseException.
  private final Runnable acceptRunnable =
      () -> {
        while (running.get()) {
          try {
            final SocketChannel sc = serverChannel.accept();
            if (sc != null) {
              new ClientSocket(Channels.newInputStream(sc), Channels.newOutputStream(sc), sc);
            }
          } catch (final IOException e) {
            running.set(false);
          }
        }
      };

  public BootServerSocket(final AppConfiguration configuration, final long farmHash)
      throws ServerAlreadyBootingException, IOException {
    final Path base = configuration.baseDirectory().toPath().toRealPath();
    final String socketPath = socketLocation(base, farmHash);
    final Path target = Paths.get(socketPath).getParent();
    if (!Files.isDirectory(target)) Files.createDirectories(target);
    socketFile = Paths.get(socketPath);
    serverChannel = newChannel(socketPath);
    running.set(true);
    acceptFuture = service.submit(acceptRunnable);
  }

  public static String socketLocation(final Path base, final long farmHash)
      throws UnsupportedEncodingException, IOException {
    final String runtimeDir =
        System.getenv().getOrDefault("XDG_RUNTIME_DIR", System.getProperty("java.io.tmpdir"));
    final Path locationForSocket =
        Paths.get(runtimeDir).resolve(".sbt").resolve("sbt-socket" + farmHash);
    return locationForSocket.resolve("sbt-load.sock").toString();
  }

  public static String namedPipeLocation(final long farmHash) {
    return "sbt-load" + farmHash;
  }

  @SuppressWarnings("EmptyCatchBlock")
  @Override
  public void close() {
    if (closed.compareAndSet(false, true)) {
      // avoid concurrent modification exception
      clientSockets.forEach(ClientSocket::close);
      if (acceptFuture != null) acceptFuture.cancel(true);
      service.shutdownNow();
      try {
        serverChannel.close();
      } catch (final IOException e) {
      }
      try {
        if (socketFile != null) Files.deleteIfExists(socketFile);
      } catch (final IOException e) {
      }
    }
  }

  /**
   * Creates a Unix domain ServerSocketChannel at the given path, replacing any stale socket file.
   * Throws ServerAlreadyBootingException if the channel cannot be bound.
   */
  static ServerSocketChannel newChannel(final String sock) throws ServerAlreadyBootingException {
    try {
      Files.deleteIfExists(Paths.get(sock));
      final ServerSocketChannel channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
      channel.bind(UnixDomainSocketAddress.of(sock));
      return channel;
    } catch (final IOException e) {
      throw new ServerAlreadyBootingException(e);
    }
  }
}
