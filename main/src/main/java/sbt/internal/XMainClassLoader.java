/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.net.URL;
import java.net.URLClassLoader;

class XMainClassLoader extends URLClassLoader {
  XMainClassLoader(final URL[] urls, final ClassLoader parent) {
    super(urls, parent);
  }

  @Override
  protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    if (name.startsWith("sbt.internal.XMainConfiguration")) {
      synchronized (getClassLoadingLock(name)) {
        Class<?> result = findLoadedClass(name);
        if (result == null) result = findClass(name);
        if (resolve) resolveClass(result);
        return result;
      }
    }
    return super.loadClass(name, resolve);
  }

  static {
    registerAsParallelCapable();
  }
}
