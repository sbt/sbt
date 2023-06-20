/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import java.net.URL;
import sbt.util.Logger;

final class LayeredClassLoader extends ManagedClassLoader {
  LayeredClassLoader(
      final URL[] classpath,
      final ClassLoader parent,
      final File tempDir,
      final boolean close,
      final boolean allowZombies,
      final Logger logger) {
    super(classpath, parent, close, allowZombies, logger);
    setTempDir(tempDir);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }
}
