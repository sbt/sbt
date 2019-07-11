/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.classpath;

import java.net.URL;
import java.net.URLClassLoader;

public class WrappedLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  WrappedLoader(final ClassLoader parent) {
    super(new URL[] {}, parent);
  }

  @Override
  public URL[] getURLs() {
    final ClassLoader parent = getParent();
    return (parent instanceof URLClassLoader)
        ? ((URLClassLoader) parent).getURLs()
        : super.getURLs();
  }

  @Override
  public String toString() {
    return "WrappedClassLoader(" + getParent() + ")";
  }
}
