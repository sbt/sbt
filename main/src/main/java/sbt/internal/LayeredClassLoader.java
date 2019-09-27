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

final class LayeredClassLoader extends ManagedClassLoader {
  LayeredClassLoader(final URL[] classpath, final ClassLoader parent, final File tempDir, final
      boolean allowZombies, final Logger logger) {
    super(classpath, parent, allowZombies, logger);
    setTempDir(tempDir);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }
}
