/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.worker;

import java.io.InputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

public final class WorkerMain {
  public static void main(final String[] args) throws Exception {
    try {
      if (args.length != 2) {
        System.err.println("missing args");
        System.exit(1);
      }
      if (args[0].equals("run")) {
        WorkerMain app = new WorkerMain();
        app.run(Paths.get(args[1]));
        System.exit(0);
      } else {
        System.exit(1);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  void run(String mainClassName, List<Path> classpath, List<String> args) throws Exception {
    URL[] urls =
        classpath
            .stream()
            .map(
                path -> {
                  try {
                    return path.toUri().toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(URL[]::new);
    URLClassLoader cl = new URLClassLoader(urls, ClassLoader.getSystemClassLoader());
    try {
      Class<?> mainClass = cl.loadClass(mainClassName);
      Method mainMethod = mainClass.getMethod("main", String[].class);
      String[] mainArgs = args.stream().toArray(String[]::new);
      mainMethod.invoke(null, (Object) mainArgs);
    } finally {
      cl.close();
    }
  }

  void run(Properties props) throws Exception {
    String mainClass = props.getProperty("mainClass");
    Enumeration<?> names0 = props.propertyNames();
    LinkedList<String> names = new LinkedList<>();
    LinkedList<String> args = new LinkedList<>();
    LinkedList<Path> classpath = new LinkedList<>();
    while (names0.hasMoreElements()) {
      String name = names0.nextElement().toString();
      names.add(name);
    }
    Collections.sort(names);
    for (String name : names) {
      String v = props.getProperty(name);
      if (name.startsWith("classpath")) {
        classpath.add(Paths.get(v));
      } else if (name.startsWith("args")) {
        args.add(v);
      }
    }
    run(mainClass, classpath, args);
  }

  void run(Path propPath) throws Exception {
    Properties props = new Properties();
    InputStream inputStream = Files.newInputStream(propPath);
    try {
      props.load(inputStream);
      run(props);
    } finally {
      inputStream.close();
    }
  }
}
