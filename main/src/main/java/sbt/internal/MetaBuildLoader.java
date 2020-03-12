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

  MetaBuildLoader(
      final URL[] urls,
      final URLClassLoader fullScalaLoader,
      final URLClassLoader libraryLoader,
      final URLClassLoader interfaceLoader) {
    super(urls, fullScalaLoader);
    this.fullScalaLoader = fullScalaLoader;
    this.libraryLoader = libraryLoader;
    this.interfaceLoader = interfaceLoader;
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
    final Pattern pattern = Pattern.compile("test-interface-[0-9.]+\\.jar");
    final File[] cp = appProvider.mainClasspath();
    final URL[] interfaceURL = new URL[1];
    final File[] extra =
        appProvider.id().classpathExtra() == null ? new File[0] : appProvider.id().classpathExtra();
    final Set<File> bottomClasspath = new LinkedHashSet<>();

    {
      for (final File file : cp) {
        if (pattern.matcher(file.getName()).find()) {
          interfaceURL[0] = file.toURI().toURL();
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
    final ClassLoader topLoader = scalaProvider.launcher().topLoader();
    final TestInterfaceLoader interfaceLoader = new TestInterfaceLoader(interfaceURL, topLoader);
    final File[] siJars = scalaProvider.jars();
    final URL[] lib = new URL[1];
    final URL[] scalaRest = new URL[Math.max(0, siJars.length - 1)];

    {
      int i = 0;
      int j = 0; // index into scalaRest
      while (i < siJars.length) {
        final File file = siJars[i];
        if (file.getName().equals("scala-library.jar")) {
          lib[0] = file.toURI().toURL();
        } else {
          scalaRest[j] = file.toURI().toURL();
          j += 1;
        }
        i += 1;
      }
    }
    assert lib[0] != null : "no scala-library.jar";
    final ScalaLibraryClassLoader libraryLoader = new ScalaLibraryClassLoader(lib, interfaceLoader);
    final FullScalaLoader fullScalaLoader = new FullScalaLoader(scalaRest, libraryLoader);
    return new MetaBuildLoader(rest, fullScalaLoader, libraryLoader, interfaceLoader);
  }
}
