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

final class FlatLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  FlatLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  public String toString() {
    final StringBuilder jars = new StringBuilder();
    for (final URL u : getURLs()) {
      jars.append("    ");
      jars.append(u);
      jars.append("\n");
    }
    return "FlatLoader(\n  parent = " + getParent() + "\n  jars = " + jars.toString() + ")";
  }

  @Override
  public void close() throws IOException {
    if (SysProp.closeClassLoaders()) super.close();
  }
}
