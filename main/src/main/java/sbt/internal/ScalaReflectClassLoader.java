/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

final class ScalaReflectClassLoader extends URLClassLoader {
  static {
    ClassLoader.registerAsParallelCapable();
  }

  private final URL jar;

  ScalaReflectClassLoader(final URL jar, final ClassLoader parent) {
    super(new URL[] {jar}, parent);
    this.jar = jar;
  }

  @Override
  public String toString() {
    return "ScalaReflectClassLoader(" + jar + " parent = " + getParent() + ")";
  }
}
