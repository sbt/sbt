/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

class JLineLoader extends URLClassLoader {
  JLineLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  public String toString() {
    final StringBuilder result = new StringBuilder();
    result.append("JLineLoader(");
    final URL[] urls = getURLs();
    for (int i = 0; i < urls.length; ++i) {
      result.append(urls[i].toString());
      if (i < urls.length - 1) result.append(", ");
    }
    result.append(")");
    return result.toString();
  }

  static {
    registerAsParallelCapable();
  }
}
