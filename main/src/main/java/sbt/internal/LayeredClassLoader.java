/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import sbt.util.Logger;
import scala.collection.Seq;

final class LayeredClassLoader extends LayeredClassLoaderImpl {
  LayeredClassLoader(final Seq<File> classpath, final ClassLoader parent, final File tempDir, final
      boolean allowZombies, final Logger logger) {
    super(classpath, parent, tempDir, allowZombies, logger);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }
}
