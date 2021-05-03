/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

// This ClassLoader must contain pure  sbt java interfaces
class SbtInterfaceLoader extends URLClassLoader {
  SbtInterfaceLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  public String toString() {
    final StringBuilder builder = new StringBuilder();
    URL[] urls = getURLs();
    for (int i = 0; i < urls.length; ++i) {
      builder.append(urls[i].toString());
      if (i < urls.length - 2) builder.append(", ");
    }
    return "SbtInterfaceClassLoader(" + builder + ")";
  }

  static {
    registerAsParallelCapable();
  }
}
