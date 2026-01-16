/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker1;

import org.scalasbt.shadedgson.com.google.gson.Gson;
import org.scalasbt.shadedgson.com.google.gson.GsonBuilder;
import org.scalasbt.shadedgson.com.google.gson.JsonElement;
import org.scalasbt.shadedgson.com.google.gson.JsonObject;
import org.scalasbt.shadedgson.com.google.gson.JsonParser;
import org.scalasbt.shadedgson.com.google.gson.JsonPrimitive;
import org.scalasbt.shadedgson.com.google.gson.typeadapters.RuntimeTypeAdapterFactory;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Scanner;
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
      } else if (args.length == 2 && args[0].equals("--tcp")) {
        WorkerMain app = new WorkerMain();
        int serverPort = Integer.parseInt(args[1]);
        app.socketWork(serverPort);
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

  void socketWork(int serverPort) throws Exception {
    InetAddress loopback = InetAddress.getByName(null);
    Socket client = new Socket(loopback, serverPort);
    this.jsonOut = new PrintStream(client.getOutputStream(), true, "UTF-8");
    this.inScanner = new Scanner(client.getInputStream(), "UTF-8");
    if (this.inScanner.hasNextLine()) {
      String line = this.inScanner.nextLine();
      process(line);
    }
  }

  /** This processes single request of supposed JSON line. */
  void process(String json) throws Exception {
    JsonElement elem = JsonParser.parseString(json);
    JsonObject o = elem.getAsJsonObject();
    if (!o.has("jsonrpc")) {
      throw new IllegalArgumentException("jsonrpc expected but got: " + json);
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
        case "bye":
          break;
      }
      String response = String.format("{ \"jsonrpc\": \"2.0\", \"result\": 0, \"id\": %d }", id);
      this.jsonOut.println(response);
      this.jsonOut.flush();
    } catch (Throwable e) {
      WorkerError err = new WorkerError(1, e.getMessage());
      String errMessage = g.toJson(err, err.getClass());
      String errJson =
          String.format("{ \"jsonrpc\": \"2.0\", \"error\": %s, \"id\": %d }", errMessage, id);
      this.jsonOut.println(errJson);
      this.jsonOut.flush();
      e.printStackTrace();
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
      try (URLClassLoader cl = createClassLoader(jvmRunInfo, parent)) {
        ForkTestMain.main(id, info, this.jsonOut, cl);
      }
    } else {
      throw new RuntimeException("only jvm is supported");
    }
  }

  private URLClassLoader createClassLoader(RunInfo.JvmRunInfo info, ClassLoader parent) {
    URL[] urls =
        info.classpath
            .stream()
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
