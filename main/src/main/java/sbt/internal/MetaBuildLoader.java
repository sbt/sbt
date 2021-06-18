/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;
import xsbti.AppProvider;
import xsbti.ScalaProvider;

@SuppressWarnings("unused")
public final class MetaBuildLoader extends URLClassLoader {
  private final URLClassLoader fullScalaLoader;
  private final URLClassLoader libraryLoader;
  private final URLClassLoader interfaceLoader;
  private final URLClassLoader jlineLoader;

  MetaBuildLoader(
      final URL[] urls,
      final URLClassLoader fullScalaLoader,
      final URLClassLoader libraryLoader,
      final URLClassLoader interfaceLoader,
      final URLClassLoader jlineLoader) {
    super(urls, fullScalaLoader);
    this.fullScalaLoader = fullScalaLoader;
    this.libraryLoader = libraryLoader;
    this.interfaceLoader = interfaceLoader;
    this.jlineLoader = jlineLoader;
  }

  @Override
  public String toString() {
    return "SbtMetaBuildClassLoader";
  }

  @Override
  public void close() throws IOException {
    super.close();
    fullScalaLoader.close();
    libraryLoader.close();
    interfaceLoader.close();
    jlineLoader.close();
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }

  /**
   * Rearrange the classloaders so that test-interface is above the scala library. Implemented
   * without using the scala standard library to minimize classloading.
   *
   * @param appProvider the appProvider that needs to be modified
   * @return a ClassLoader with a URLClassLoader for the test-interface-1.0.jar above the scala
   *     library.
   */
  public static MetaBuildLoader makeLoader(final AppProvider appProvider) throws IOException {
    final String jlineJars = "jline-?[0-9.]+-sbt-.*|jline-terminal(-(jna|jansi))?-[0-9.]+";
    final String testInterfaceJars = "test-interface(-.*)?";
    final String compilerInterfaceJars = "compiler-interface(-.*)?";
    final String utilInterfaceJars = "util-interface(-.*)?";
    final String jansiJars = "jansi-[0-9.]+";
    final String jnaJars = "jna-(platform-)?[0-9.]+";
    final String fullPattern =
        String.format(
            "^(%s|%s|%s|%s|%s|%s)\\.jar",
            jlineJars,
            testInterfaceJars,
            compilerInterfaceJars,
            utilInterfaceJars,
            jansiJars,
            jnaJars);
    final Pattern pattern = Pattern.compile(fullPattern);
    final File[] cp = appProvider.mainClasspath();
    final URL[] interfaceURLs = new URL[3];
    final URL[] jlineURLs = new URL[7];
    final File[] extra =
        appProvider.id().classpathExtra() == null ? new File[0] : appProvider.id().classpathExtra();
    final Set<File> bottomClasspath = new LinkedHashSet<>();

    {
      int interfaceIndex = 0;
      int jlineIndex = 0;
      for (final File file : cp) {
        final String name = file.getName();
        if ((name.contains("test-interface")
                || name.contains("compiler-interface")
                || name.contains("util-interface"))
            && pattern.matcher(name).find()) {
          interfaceURLs[interfaceIndex] = file.toURI().toURL();
          interfaceIndex += 1;
        } else if (pattern.matcher(name).find()) {
          jlineURLs[jlineIndex] = file.toURI().toURL();
          jlineIndex += 1;
        } else {
          bottomClasspath.add(file);
        }
      }
      for (final File file : extra) {
        bottomClasspath.add(file);
      }
    }
    final URL[] rest = new URL[bottomClasspath.size()];
    {
      int i = 0;
      for (final File file : bottomClasspath) {
        rest[i] = file.toURI().toURL();
        i += 1;
      }
    }
    final ScalaProvider scalaProvider = appProvider.scalaProvider();
    ClassLoader topLoader = scalaProvider.launcher().topLoader();
    boolean foundSBTLoader = false;
    while (!foundSBTLoader && topLoader != null) {
      if (topLoader instanceof URLClassLoader) {
        for (final URL u : ((URLClassLoader) topLoader).getURLs()) {
          if (u.toString().contains("test-interface")) {
            topLoader = topLoader.getParent();
            foundSBTLoader = true;
          }
        }
      }
      if (!foundSBTLoader) topLoader = topLoader.getParent();
    }
    if (topLoader == null) topLoader = scalaProvider.launcher().topLoader();
    // the bundled version of jansi with old versions of the launcher cause
    // problems so we need to exclude it from classloading
    topLoader =
        new ClassLoader(topLoader) {
          @Override
          protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("org.fusesource")) throw new ClassNotFoundException(name);
            return super.loadClass(name, resolve);
          }

          @Override
          public String toString() {
            return "JansiExclusionClassLoader";
          }
        };

    final SbtInterfaceLoader interfaceLoader = new SbtInterfaceLoader(interfaceURLs, topLoader);
    final JLineLoader jlineLoader = new JLineLoader(jlineURLs, interfaceLoader);
    final File[] siJars = scalaProvider.jars();
    final URL[] lib = new URL[1];
    int scalaRestCount = siJars.length - 1;
    for (final File file : siJars) {
      if (pattern.matcher(file.getName()).find()) scalaRestCount -= 1;
    }
    final URL[] scalaRest = new URL[Math.max(0, scalaRestCount)];

    {
      int i = 0;
      int j = 0; // index into scalaRest
      while (i < siJars.length) {
        final File file = siJars[i];
        if (file.getName().equals("scala-library.jar")) {
          lib[0] = file.toURI().toURL();
        } else if (!pattern.matcher(file.getName()).find()) {
          scalaRest[j] = file.toURI().toURL();
          j += 1;
        }
        i += 1;
      }
    }
    assert lib[0] != null : "no scala-library.jar";
    final ScalaLibraryClassLoader libraryLoader = new ScalaLibraryClassLoader(lib, jlineLoader);
    final FullScalaLoader fullScalaLoader = new FullScalaLoader(scalaRest, libraryLoader);
    return new MetaBuildLoader(rest, fullScalaLoader, libraryLoader, interfaceLoader, jlineLoader);
  }
}
