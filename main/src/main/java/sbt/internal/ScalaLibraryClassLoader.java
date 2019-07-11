/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;

final class ScalaLibraryClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final URL[] jars;

  ScalaLibraryClassLoader(final URL[] jars, final ClassLoader parent) {
    super(jars, parent);
    this.jars = jars;
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    for (int i = 0; i < jars.length; ++ i) {
      builder.append(jars[i].toString());
      if (i < jars.length - 2) builder.append(", ");
    }
    return "ScalaLibraryClassLoader(" + builder + " parent = " + getParent() + ")";
  }

  @Override
  public void close() throws IOException {
    if (SysProp.closeClassLoaders()) super.close();
  }
}
