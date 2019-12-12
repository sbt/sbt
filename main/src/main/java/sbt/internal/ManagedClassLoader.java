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
import java.util.function.Supplier;
import sbt.util.Logger;

abstract class ManagedClassLoader extends URLClassLoader implements NativeLoader {
  private final AtomicBoolean closed = new AtomicBoolean(false);
  private final AtomicBoolean printedWarning = new AtomicBoolean(false);
  private final AtomicReference<ZombieClassLoader> zombieLoader = new AtomicReference<>();
  private final boolean allowZombies;
  private final Logger logger;
  private final NativeLookup nativeLookup = new NativeLookup();

  static {
    ClassLoader.registerAsParallelCapable();
  }

  ManagedClassLoader(
      final URL[] urls, final ClassLoader parent, final boolean allowZombies, final Logger logger) {
    super(urls, parent);
    this.allowZombies = allowZombies;
    this.logger = logger;
  }

  private class ZombieClassLoader extends URLClassLoader {
    private final URL[] urls;

    ZombieClassLoader(URL[] urls) {
      super(urls, ManagedClassLoader.this.getParent());
      this.urls = urls;
    }

    Class<?> lookupClass(final String name) throws ClassNotFoundException {
      try {
        synchronized (getClassLoadingLock(name)) {
          final Class<?> previous = findLoadedClass(name);
          return previous != null ? previous : findClass(name);
        }
      } catch (final ClassNotFoundException e) {
        final StringBuilder builder = new StringBuilder();
        for (final URL u : urls) {
          final File f = new File(u.getPath());
          if (f.exists()) builder.append(f.toString()).append('\n');
        }
        final String deleted = builder.toString();
        if (!deleted.isEmpty()) {
          final String msg =
              "Couldn't load class $name. "
                  + "The following urls on the classpath do not exist:\n"
                  + deleted
                  + "This may be due to shutdown hooks added during an invocation of `run`.";
          System.err.println(msg);
        }
        throw e;
      }
    }
  }

  private ZombieClassLoader getZombieLoader(final String name) {
    if (printedWarning.compareAndSet(false, true) && !allowZombies) {
      final String msg =
          (Thread.currentThread() + " loading " + name + " after test or run ")
              + "has completed. This is a likely resource leak";
      logger.warn((Supplier<String>) () -> msg);
    }
    final ZombieClassLoader maybeLoader = zombieLoader.get();
    if (maybeLoader != null) return maybeLoader;
    else {
      final ZombieClassLoader zb = new ZombieClassLoader(getURLs());
      zombieLoader.set(zb);
      return zb;
    }
  }

  @Override
  public URL findResource(String name) {
    return closed.get() ? getZombieLoader(name).findResource(name) : super.findResource(name);
  }

  @Override
  protected Class<?> findClass(String name) throws ClassNotFoundException {
    try {
      return super.findClass(name);
    } catch (final NoClassDefFoundError | ClassNotFoundException e) {
      if (closed.get()) return getZombieLoader(name).lookupClass(name);
      else throw e;
    }
  }

  @Override
  public void close() throws IOException {
    final ZombieClassLoader zb = zombieLoader.getAndSet(null);
    if (zb != null) zb.close();
    if (closed.compareAndSet(false, true)) super.close();
  }

  @Override
  public String findLibrary(final String name) {
    return nativeLookup.findLibrary(name);
  }

  @Override
  public void setTempDir(final File file) {
    nativeLookup.setTempDir(file);
  }
}
