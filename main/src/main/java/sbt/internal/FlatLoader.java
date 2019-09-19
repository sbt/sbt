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
import scala.collection.Seq;

final class FlatLoader extends LayeredClassLoaderImpl {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  FlatLoader(
      final Seq<File> files,
      final ClassLoader parent,
      final File file,
      final boolean allowZombies,
      final Logger logger) {
    super(files, parent, file, allowZombies, logger);
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
