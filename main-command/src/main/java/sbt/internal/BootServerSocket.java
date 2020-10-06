/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.net.ServerSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
import net.openhft.hashing.LongHashFunction;
import org.scalasbt.ipcsocket.UnixDomainServerSocket;
import org.scalasbt.ipcsocket.UnixDomainSocket;
import org.scalasbt.ipcsocket.Win32NamedPipeServerSocket;
import org.scalasbt.ipcsocket.Win32NamedPipeSocket;
import org.scalasbt.ipcsocket.Win32SecurityLevel;
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
 * socket before sbt even starts loading the build. It works by creating a local socket either in
 * project/target/SOCK_NAME or a windows named pipe with name SOCK_NAME where SOCK_NAME is computed
 * as the hash of the project's base directory (for disambiguation in the windows case). If the
 * server can't create a server socket because there is already one running, it either prompts the
 * user if they want to start a new server even if there is already one running if there is a
 * console available or exits with the status code 2 which indicates that there is another sbt
 * process starting up.
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
  private ServerSocket serverSocket = null;
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

  private class ClientSocket implements AutoCloseable {
    final Socket socket;
    final AtomicBoolean alive = new AtomicBoolean(true);
    final Future<?> future;
    private final LinkedBlockingQueue<Integer> bytes = new LinkedBlockingQueue<Integer>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    @SuppressWarnings("deprecation")
    ClientSocket(final Socket socket) {
      this.socket = socket;
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
                    final InputStream inputStream = socket.getInputStream();
                    while (alive.get()) {
                      try {
                        synchronized (needInput) {
                          while (!needInput.get() && alive.get()) needInput.wait();
                        }
                        if (alive.get()) {
                          socket.getOutputStream().write(5);
                          int b = inputStream.read();
                          if (b != -1) {
                            bytes.put(b);
                            clientSocketReads.put(ClientSocket.this);
                          } else {
                            alive.set(false);
                          }
                        }

                      } catch (IOException e) {
                        alive.set(false);
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
        if (alive.get()) socket.getOutputStream().write(i);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void write(final byte[] b) {
      try {
        if (alive.get()) socket.getOutputStream().write(b);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void write(final byte[] b, final int offset, final int len) {
      try {
        if (alive.get()) socket.getOutputStream().write(b, offset, len);
      } catch (final IOException e) {
        alive.set(false);
        close();
      }
    }

    private void flush() {
      try {
        socket.getOutputStream().flush();
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
          socket.getOutputStream().close();
          socket.getInputStream().close();
          // Windows is very slow to close the socket for whatever reason
          // We close the server socket anyway, so this should die then.
          if (!System.getProperty("os.name", "").toLowerCase().startsWith("win")) socket.close();
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

  private final Runnable acceptRunnable =
      () -> {
        try {
          serverSocket.setSoTimeout(5000);
          while (running.get()) {
            try {
              ClientSocket clientSocket = new ClientSocket(serverSocket.accept());
            } catch (final SocketTimeoutException e) {
            } catch (final IOException e) {
              running.set(false);
            }
          }
        } catch (final SocketException e) {
        }
      };

  public BootServerSocket(final AppConfiguration configuration)
      throws ServerAlreadyBootingException, IOException {
    final Path base = configuration.baseDirectory().toPath().toRealPath();
    final Path target = base.resolve("project").resolve("target");
    if (!isWindows) {
      socketFile = Paths.get(socketLocation(base));
      Files.createDirectories(target);
    } else {
      socketFile = null;
    }
    serverSocket = newSocket(socketLocation(base));
    if (serverSocket != null) {
      running.set(true);
      acceptFuture = service.submit(acceptRunnable);
    } else {
      closed.set(true);
      acceptFuture = null;
    }
  }

  public static String socketLocation(final Path base) throws UnsupportedEncodingException {
    final Path target = base.resolve("project").resolve("target");
    if (isWindows) {
      long hash = LongHashFunction.farmNa().hashBytes(target.toString().getBytes("UTF-8"));
      return "sbt-load" + hash;
    } else {
      return base.relativize(target.resolve("sbt-load.sock")).toString();
    }
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
        if (serverSocket != null) serverSocket.close();
      } catch (final IOException e) {
      }
      try {
        if (socketFile != null) Files.deleteIfExists(socketFile);
      } catch (final IOException e) {
      }
    }
  }

  static final boolean isWindows =
      System.getProperty("os.name", "").toLowerCase().startsWith("win");

  static ServerSocket newSocket(final String sock) throws ServerAlreadyBootingException {
    ServerSocket socket = null;
    String name = socketName(sock);
    try {
      if (!isWindows) Files.deleteIfExists(Paths.get(sock));
      socket =
          isWindows
              ? new Win32NamedPipeServerSocket(name, false, Win32SecurityLevel.OWNER_DACL)
              : new UnixDomainServerSocket(name);
      return socket;
    } catch (final IOException e) {
      throw new ServerAlreadyBootingException();
    }
  }

  private static String socketName(String sock) {
    return isWindows ? "\\\\.\\pipe\\" + sock : sock;
  }
}
