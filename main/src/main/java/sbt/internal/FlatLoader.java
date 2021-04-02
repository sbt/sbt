/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import java.net.URL;
import sbt.util.Logger;

final class FlatLoader extends ManagedClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  FlatLoader(
      final URL[] urls,
      final ClassLoader parent,
      final File file,
      final boolean close,
      final boolean allowZombies,
      final Logger logger) {
    super(urls, parent, close, allowZombies, logger);
    setTempDir(file);
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
}
