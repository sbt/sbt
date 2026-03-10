package sbt.internal.worker1;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import xsbti.compile.ConsoleInterface1;

public final class ForkConsoleMain {
  public void run(long id, ConsoleInfo info) throws Exception {
    try {
      Class<ConsoleInterface1> cls = ConsoleInterface1.class;
      Iterator<ConsoleInterface1> iter =
          ServiceLoader.load(cls, ForkConsoleMain.class.getClassLoader()).iterator();
      List<ConsoleInterface1> list = new ArrayList<>();
      while (iter.hasNext()) {
        list.add(iter.next());
      }
      if (list.size() > 0) {
        runInterface1(list.get(0), info);
      } else {
        runOldInterface(info);
      }
    } catch (Throwable e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  private void runInterface1(ConsoleInterface1 intf, ConsoleInfo info) throws Exception {
    String toolsJars =
        info.toolsJars.stream()
            .map(u -> Paths.get(u).toString())
            .collect(Collectors.joining(File.pathSeparator));
    String classpathJars =
        Stream.concat(info.products.stream(), info.classpathJars.stream())
            .map(u -> Paths.get(u).toString())
            .collect(Collectors.joining(File.pathSeparator));
    intf.run(
        info.scalacOptions.toArray(new String[0]),
        toolsJars,
        classpathJars,
        info.initialCommands,
        info.cleanupCommands,
        createClassLoader(info, ForkConsoleMain.class.getClassLoader()),
        new String[] {},
        new Object[] {},
        new ZeroLogger());
  }

  private void runOldInterface(ConsoleInfo info) throws Exception {
    Class<?> concrete = Class.forName("xsbt.ConsoleInterface");
    Object instance = concrete.getDeclaredConstructor().newInstance();
    Method m =
        concrete.getMethod(
            "run",
            String[].class,
            String.class,
            String.class,
            String.class,
            String.class,
            ClassLoader.class,
            String[].class,
            Object[].class,
            xsbti.Logger.class);
    String toolsJars =
        info.toolsJars.stream()
            .map(u -> Paths.get(u).toString())
            .collect(Collectors.joining(File.pathSeparator));
    String classpathJars =
        Stream.concat(info.products.stream(), info.classpathJars.stream())
            .map(u -> Paths.get(u).toString())
            .collect(Collectors.joining(File.pathSeparator));
    m.invoke(
        instance,
        info.scalacOptions.toArray(new String[0]),
        toolsJars,
        classpathJars,
        info.initialCommands,
        info.cleanupCommands,
        createClassLoader(info, concrete.getClassLoader()),
        new String[] {},
        new Object[] {},
        new ZeroLogger());
  }

  private URLClassLoader createClassLoader(ConsoleInfo info, ClassLoader parent) {
    URL[] urls =
        Stream.concat(info.products.stream(), info.classpathJars.stream())
            .map(
                u -> {
                  try {
                    return u.toURL();
                  } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                  }
                })
            .toArray(URL[]::new);
    return new URLClassLoader(urls, parent);
  }

  public static void main(long id, ConsoleInfo info) throws Exception {
    new ForkConsoleMain().run(id, info);
  }
}
