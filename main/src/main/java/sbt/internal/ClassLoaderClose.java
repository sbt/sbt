/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

public class ClassLoaderClose {
  public static void close(ClassLoader classLoader) throws Exception {
    if (classLoader instanceof AutoCloseable) ((AutoCloseable) classLoader).close();
  }
}
