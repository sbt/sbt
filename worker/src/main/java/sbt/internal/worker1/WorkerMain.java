/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import org.scalasbt.shadedgson.com.google.gson.Gson;
import org.scalasbt.shadedgson.com.google.gson.GsonBuilder;
import org.scalasbt.shadedgson.com.google.gson.JsonElement;
import org.scalasbt.shadedgson.com.google.gson.JsonObject;
import org.scalasbt.shadedgson.com.google.gson.JsonParser;
import org.scalasbt.shadedgson.com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import sbt.testing.*;

/**
 * WorkerMain that communicates via the stdio or socket using JSON-RPC
 * (https://www.jsonrpc.org/specification).
 */
public final class WorkerMain {
  private PrintStream originalOut;
  private InputStream originalIn;

  // When using stdout, this is the original stdout
  // When using tcp, this is going to be the socket out
  private PrintStream jsonOut;
  private Scanner inScanner;

  public static Gson mkGson() {
    RuntimeTypeAdapterFactory<Fingerprint> fingerprintFac =
        RuntimeTypeAdapterFactory.of(Fingerprint.class, "type");
    fingerprintFac.registerSubtype(ForkTestMain.SubclassFingerscan.class, "SubclassFingerscan");
    fingerprintFac.registerSubtype(ForkTestMain.AnnotatedFingerscan.class, "AnnotatedFingerscan");
    RuntimeTypeAdapterFactory<Selector> selectorFac =
        RuntimeTypeAdapterFactory.of(Selector.class, "type");
    selectorFac.registerSubtype(SuiteSelector.class, "SuiteSelector");
    selectorFac.registerSubtype(TestSelector.class, "TestSelector");
    selectorFac.registerSubtype(NestedSuiteSelector.class, "NestedSuiteSelector");
    selectorFac.registerSubtype(NestedTestSelector.class, "NestedTestSelector");
    selectorFac.registerSubtype(TestWildcardSelector.class, "TestWildcardSelector");
    return new GsonBuilder()
        .registerTypeAdapterFactory(fingerprintFac)
        .registerTypeAdapterFactory(selectorFac)
        .registerTypeAdapterFactory(ThrowableAdapterFactory.INSTANCE)
        .create();
  }

  public static void main(final String[] args) throws Exception {
    try {
      if (args.length == 0) {
        WorkerMain app = new WorkerMain();
        app.consoleWork();
        System.exit(0);
      } else if (args.length == 1 && args[0].startsWith("@")) {
        WorkerMain app = new WorkerMain();
        app.argFileWork(Paths.get(args[0].substring(1)));
        System.exit(0);
      } else if (args.length >= 2 && args[0].equals("--tcp")) {
        WorkerMain app = new WorkerMain();
        int serverPort = Integer.parseInt(args[1]);
        boolean persistentWorker = Arrays.asList(args).contains("--persistent_worker");
        app.socketWork(serverPort, persistentWorker);
        System.exit(0);
      } else if (args.length == 2 && args[0].equals("--ipc")) {
        WorkerMain app = new WorkerMain();
        app.ipcWork(Paths.get(args[1]));
        System.exit(0);
      } else {
        System.err.println("missing args");
        System.exit(1);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  WorkerMain() {
    this.originalOut = System.out;
    this.originalIn = System.in;
    this.jsonOut = this.originalOut;
  }

  void consoleWork() throws Exception {
    this.jsonOut = this.originalOut;
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    System.setOut(new PrintStream(baos));
    this.inScanner = new Scanner(this.originalIn, "UTF-8");
    if (this.inScanner.hasNextLine()) {
      String line = this.inScanner.nextLine();
      process(line);
    }
  }

  void argFileWork(Path arg) throws Exception {
    this.jsonOut = this.originalOut;
    byte[] encoded = Files.readAllBytes(arg);
    String line = new String(encoded, "UTF-8");
    process(line);
  }

  void socketWork(int serverPort, boolean persistentWorker) throws Exception {
    InetAddress loopback = InetAddress.getByName(null);
    Socket client = new Socket(loopback, serverPort);
    this.jsonOut = new PrintStream(client.getOutputStream(), true, "UTF-8");
    this.inScanner = new Scanner(client.getInputStream(), "UTF-8");
    boolean keepGoing = true;
    while (keepGoing && this.inScanner.hasNextLine()) {
      String line = this.inScanner.nextLine();
      keepGoing = process(line) && persistentWorker;
      if (keepGoing) {
        client.setSoTimeout(30 * 60 * 1000);
      }
    }
  }

  void ipcWork(Path socketPath) throws Exception {
    SocketChannel client = JdkCompat.connectUnixSocket(socketPath);
    this.jsonOut = new PrintStream(DuplexChannels.newOutputStream(client), true, "UTF-8");
    this.inScanner = new Scanner(DuplexChannels.newInputStream(client), "UTF-8");
    if (this.inScanner.hasNextLine()) {
      String line = this.inScanner.nextLine();
      process(line);
    }
  }

  /** This processes single request of supposed JSON line. */
  boolean process(String json) throws Exception {
    JsonElement elem = JsonParser.parseString(json);
    JsonObject o = elem.getAsJsonObject();
    if (!o.has("jsonrpc")) {
      // Exit without stack trace so CI / test runners do not treat stderr as failure
      System.exit(1);
    }
    Gson g = WorkerMain.mkGson();
    long id = o.getAsJsonPrimitive("id").getAsLong();
    try {
      String method = o.getAsJsonPrimitive("method").getAsString();
      JsonObject params = o.getAsJsonObject("params");
      switch (method) {
        case "run":
          RunInfo info = g.fromJson(params, RunInfo.class);
          run(info);
          break;
        case "test":
          TestInfo testInfo = g.fromJson(params, TestInfo.class);
          test(id, testInfo);
          break;
        case "console":
          ConsoleInfo consoleInfo = g.fromJson(params, ConsoleInfo.class);
          console(id, consoleInfo);
          return false;
        case "bye":
          break;
      }
      String response = String.format("{ \"jsonrpc\": \"2.0\", \"result\": 0, \"id\": %d }", id);
      this.jsonOut.println(response);
      this.jsonOut.flush();
      return !method.equals("bye");
    } catch (Throwable e) {
      WorkerError err = new WorkerError(1, e.getMessage());
      String errMessage = g.toJson(err, err.getClass());
      String errJson =
          String.format("{ \"jsonrpc\": \"2.0\", \"error\": %s, \"id\": %d }", errMessage, id);
      this.jsonOut.println(errJson);
      this.jsonOut.flush();
      e.printStackTrace();
      return false;
    }
  }

  void run(RunInfo info) throws Exception {
    if (info.jvm) {
      if (info.jvmRunInfo == null) {
        throw new RuntimeException("missing jvmRunInfo element");
      }
      RunInfo.JvmRunInfo jvmRunInfo = info.jvmRunInfo;
      try (URLClassLoader cl = createClassLoader(jvmRunInfo, ClassLoader.getSystemClassLoader())) {
        Class<?> mainClass = cl.loadClass(jvmRunInfo.mainClass);
        Method mainMethod = mainClass.getMethod("main", String[].class);
        String[] mainArgs = jvmRunInfo.args.stream().toArray(String[]::new);
        mainMethod.invoke(null, (Object) mainArgs);
      }
    } else {
      throw new RuntimeException("only jvm is supported");
    }
  }

  void test(long id, TestInfo info) throws Exception {
    if (info.jvm) {
      RunInfo.JvmRunInfo jvmRunInfo = info.jvmRunInfo;
      ClassLoader parent = new ForkTestMain().getClass().getClassLoader();
      // empty virtual classpath means raw mode
      if (jvmRunInfo.classpath.isEmpty()) {
        ForkTestMain.main(id, info, this.jsonOut, parent);
      } else {
        ForkTestMain.main(id, info, this.jsonOut, classLoaderFor(jvmRunInfo, parent));
      }
    } else {
      throw new RuntimeException("only jvm is supported");
    }
  }

  private Set<FilePath> stableLayerEntries = Collections.emptySet();
  private URLClassLoader stableLayer;
  private URLClassLoader topLayer;

  /** Caches non-build-output (library) entries as a parent layer; rebuilds only "target" output. */
  private URLClassLoader classLoaderFor(RunInfo.JvmRunInfo info, ClassLoader parent)
      throws IOException {
    List<FilePath> stable = new ArrayList<>();
    List<FilePath> changed = new ArrayList<>();
    for (FilePath fp : info.classpath) {
      (isBuildOutput(fp) ? changed : stable).add(fp);
    }

    Set<FilePath> stableSet = new HashSet<>(stable);
    if (stableLayer == null || !stableLayerEntries.equals(stableSet)) {
      stableLayer = urlClassLoaderOf(stable, parent);
      stableLayerEntries = stableSet;
    }

    if (topLayer != null) topLayer.close();
    topLayer = changed.isEmpty() ? null : urlClassLoaderOf(changed, stableLayer);
    return topLayer != null ? topLayer : stableLayer;
  }

  private static boolean isBuildOutput(FilePath fp) {
    String path = fp.path.getPath();
    return path != null && (path.contains("/target/") || path.contains("\\target\\"));
  }

  private URLClassLoader urlClassLoaderOf(List<FilePath> entries, ClassLoader parent) {
    URL[] urls =
        entries.stream()
            .map(
                filePath -> {
                  try {
                    return filePath.path.toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, parent);
  }

  void console(long id, ConsoleInfo info) throws Exception {
    ForkConsoleMain.main(id, info);
    return;
  }

  private URLClassLoader createClassLoader(RunInfo.JvmRunInfo info, ClassLoader parent) {
    URL[] urls =
        info.classpath.stream()
            .map(
                filePath -> {
                  try {
                    return filePath.path.toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, parent);
  }
}
