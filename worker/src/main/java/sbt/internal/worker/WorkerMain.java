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
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp.Capability;

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
    overrideJLineTerminal();
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

  /*
   * val res1: org.jline.terminal.Attributes = Attributes[
   * lflags: echoke echoe echok echoctl isig,
   * iflags: brkint ixany imaxbel iutf8,
   * oflags: opost onlcr,
   * cflags: cs6 cs7 cs8 cread hupcl,
   * cchars: eof=^D eol=<undef> eol2=<undef> erase=^? werase=^W kill=^U reprint=^R intr=^C quit=^\
   * susp=^Z dsusp=^Y start=^Q stop=^S lnext=^V discard=^O min=1 time=0 status=^T]
   */
  void overrideJLineTerminal() throws Exception {
    Attributes attr = new Attributes();
    attr.setLocalFlag(Attributes.LocalFlag.ECHOKE, true);
    attr.setLocalFlag(Attributes.LocalFlag.ECHOE, true);
    attr.setLocalFlag(Attributes.LocalFlag.ECHOK, true);
    attr.setLocalFlag(Attributes.LocalFlag.ECHOCTL, true);
    attr.setLocalFlag(Attributes.LocalFlag.ISIG, true);
    attr.setInputFlag(Attributes.InputFlag.BRKINT, true);
    attr.setInputFlag(Attributes.InputFlag.IXANY, true);
    attr.setInputFlag(Attributes.InputFlag.IMAXBEL, true);
    attr.setInputFlag(Attributes.InputFlag.IUTF8, true);
    attr.setControlFlag(Attributes.ControlFlag.CS6, true);
    attr.setControlFlag(Attributes.ControlFlag.CS7, true);
    attr.setControlFlag(Attributes.ControlFlag.CS8, true);
    attr.setControlFlag(Attributes.ControlFlag.CREAD, true);
    attr.setControlFlag(Attributes.ControlFlag.HUPCL, true);
    attr.setOutputFlag(Attributes.OutputFlag.OPOST, true);
    attr.setOutputFlag(Attributes.OutputFlag.ONLCR, true);
    Terminal terminal =
        TerminalBuilder.builder()
            .type("xterm-256color")
            .system(false)
            .attributes(attr)
            // .streams(new BufferedInputStream(System.in), System.out)
            .paused(true)
            .size(new org.jline.terminal.Size(100, 100))
            .build();

    // terminal.puts(Capability.key_up, "k");
    TerminalBuilder.setTerminalOverride(terminal);
  }
}
