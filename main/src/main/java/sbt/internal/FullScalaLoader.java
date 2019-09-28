/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

final class FullScalaLoader extends URLClassLoader {
  private final String jarString;

  FullScalaLoader(final URL[] scalaRest, final URLClassLoader parent) {
    super(scalaRest, parent);
    final StringBuilder res = new StringBuilder();
    int i = 0;
    while (i < scalaRest.length) {
      res.append(scalaRest[i].getPath());
      res.append(", ");
      i += 1;
    }
    jarString = res.toString();
  }

  @Override
  public String toString() {
    return "ScalaClassLoader(jars = " + jarString + ")";
  }

  static {
    registerAsParallelCapable();
  }
}
