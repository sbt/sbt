/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

class TestInterfaceLoader extends URLClassLoader {
  TestInterfaceLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  public String toString() {
    return "SbtTestInterfaceClassLoader(" + getURLs()[0] + ")";
  }

  static {
    registerAsParallelCapable();
  }
}
