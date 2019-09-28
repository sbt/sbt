/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import sbt.util.Logger;

/**
 * The bottom most layer in our layering hierarchy. This layer should never be cached. The
 * dependency layer may need access to classes only available at this layer using java reflection.
 * To make this work, we register this loader with the parent in its constructor. We also add the
 * lookupClass method which gives ReverseLookupClassLoader a public interface to findClass.
 *
 * <p>To improve performance, when loading classes from the parent, we call the loadClass method
 * with the reverseLookup flag set to false. This prevents the ReverseLookupClassLoader from trying
 * to call back into this loader when it can't find a particular class.
 */
final class BottomClassLoader extends ManagedClassLoader {
  private final ReverseLookupClassLoaderHolder holder;
  private final ReverseLookupClassLoader parent;
  private final ClassLoadingLock classLoadingLock = new ClassLoadingLock();

  BottomClassLoader(
      final ReverseLookupClassLoaderHolder holder,
      final URL[] dynamicClasspath,
      final ReverseLookupClassLoader reverseLookupClassLoader,
      final File tempDir,
      final boolean allowZombies,
      final Logger logger) {
    super(dynamicClasspath, reverseLookupClassLoader, allowZombies, logger);
    setTempDir(tempDir);
    this.holder = holder;
    this.parent = reverseLookupClassLoader;
    parent.setDescendant(this);
  }

  static {
    ClassLoader.registerAsParallelCapable();
  }

  @Override
  public Class<?> findClass(final String name) throws ClassNotFoundException {
    return classLoadingLock.withLock(
        name,
        () -> {
          final Class<?> prev = findLoadedClass(name);
          if (prev != null) return prev;
          return super.findClass(name);
        });
  }

  @Override
  protected Class<?> loadClass(final String name, final boolean resolve)
      throws ClassNotFoundException {
    try {
      return parent.loadClass(name, resolve, false);
    } catch (final ClassNotFoundException e) {
      final Class<?> clazz = findClass(name);
      if (resolve) resolveClass(clazz);
      return clazz;
    }
  }

  @Override
  public void close() throws IOException {
    holder.checkin(parent);
    super.close();
  }
}
