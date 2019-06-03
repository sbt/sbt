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

class ResourceLoader extends ResourceLoaderImpl {
  ResourceLoader(
      final Seq<File> classpath, final ClassLoader parent, final Map<String, String> resources) {
    super(classpath, parent, resources);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }
}
