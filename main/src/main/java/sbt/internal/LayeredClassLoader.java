/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import scala.collection.immutable.Map;
import scala.collection.Seq;

class LayeredClassLoader extends LayeredClassLoaderImpl {
  LayeredClassLoader(
      final Seq<File> classpath,
      final ClassLoader parent,
      final Map<String, String> resources,
      final File tempDir) {
    super(classpath, parent, resources, tempDir);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }
}
