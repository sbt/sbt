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
import java.net.URLClassLoader;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import sbt.util.Logger;

/**
 * A ClassLoader for the dependency layer of a run or test task. It is almost a normal
 * URLClassLoader except that it has the ability to look one level down the classloading hierarchy
 * to load a class via reflection that is not directly available to it. The ClassLoader that is
 * below it in the hierarchy must be registered via setDescendant. If it ever loads a class from its
 * descendant, then it cannot be used in a subsequent run because it will not be possible to reload
 * that class.
 *
 * <p>The descendant classloader checks it in and out via [[checkout]] and [[checkin]]. When it
 * returns the loader via [[checkin]], if the loader is dirty, we close it. Otherwise we cache it if
 * there is no existing cache entry.
 *
 * <p>Performance degrades if loadClass is constantly looking back up to the provided
 * BottomClassLoader so we provide an alternate loadClass definition that takes a reverseLookup
 * boolean parameter. Because the [[BottomClassLoader]] knows what loader is calling into, when it
 * delegates its search to the ReverseLookupClassLoader, it passes false for the reverseLookup flag.
 * By default the flag is true. Most of the time, the default loadClass will only be invoked by java
 * reflection calls. Even then, there's some chance that the class being loaded by java reflection
 * is _not_ available on the bottom classpath so it is not guaranteed that performing the reverse
 * lookup will invalidate this loader.
 */
final class ReverseLookupClassLoader extends ManagedClassLoader {
  ReverseLookupClassLoader(
      final URL[] urls, final ClassLoader parent, final boolean allowZombies, final Logger logger) {
    super(urls, parent, allowZombies, logger);
    this.parent = parent;
  }

  private final AtomicReference<BottomClassLoader> directDescendant = new AtomicReference<>();
  private final AtomicBoolean dirty = new AtomicBoolean(false);
  private final ClassLoadingLock classLoadingLock = new ClassLoadingLock();
  private final AtomicReference<ResourceLoader> resourceLoader = new AtomicReference<>();
  private final ClassLoader parent;

  boolean isDirty() {
    return dirty.get();
  }

  void setDescendant(final BottomClassLoader bottomClassLoader) {
    directDescendant.set(bottomClassLoader);
  }

  private class ResourceLoader extends URLClassLoader {
    ResourceLoader(final URL[] urls) {
      super(urls, parent);
    }

    final URL lookup(final String name) {
      return findResource(name);
    }
  }

  @Override
  public URL findResource(String name) {
    final ResourceLoader loader = resourceLoader.get();
    return loader == null ? null : loader.lookup(name);
  }

  void setup(final File tmpDir, final URL[] urls) throws IOException {
    setTempDir(tmpDir);
    final ResourceLoader previous = resourceLoader.getAndSet(new ResourceLoader(urls));
    if (previous != null) previous.close();
  }

  @Override
  protected Class<?> loadClass(final String name, final boolean resolve)
      throws ClassNotFoundException {
    return loadClass(name, resolve, true);
  }

  Class<?> loadClass(final String name, final boolean resolve, final boolean childLookup)
      throws ClassNotFoundException {
    Class<?> result;
    try {
      result = parent.loadClass(name);
    } catch (final ClassNotFoundException e) {
      result = findClass(name, childLookup);
    }
    if (result == null) throw new ClassNotFoundException(name);
    if (resolve) resolveClass(result);
    return result;
  }

  private Class<?> findClass(final String name, final boolean childLookup)
      throws ClassNotFoundException {
    return classLoadingLock.withLock(
        name,
        () -> {
          try {
            final Class<?> prev = findLoadedClass(name);
            if (prev != null) return prev;
            return findClass(name);
          } catch (final ClassNotFoundException e) {
            if (childLookup) {
              final BottomClassLoader loader = directDescendant.get();
              if (loader == null) throw e;
              final Class<?> clazz = loader.findClass(name);
              dirty.set(true);
              return clazz;
            } else {
              throw e;
            }
          }
        });
  }

  static {
    registerAsParallelCapable();
  }
}
