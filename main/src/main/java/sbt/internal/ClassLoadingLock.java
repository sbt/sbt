/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal;

import java.util.concurrent.ConcurrentHashMap;

final class ClassLoadingLock {
  interface ThrowsClassNotFound<R> {
    R get() throws ClassNotFoundException;
  }

  private final ConcurrentHashMap<String, Object> locks = new ConcurrentHashMap<>();

  <R> R withLock(final String name, final ThrowsClassNotFound<R> supplier)
      throws ClassNotFoundException {
    final Object newLock = new Object();
    Object prevLock;
    synchronized (locks) {
      prevLock = locks.putIfAbsent(name, newLock);
    }
    final Object lock = prevLock == null ? newLock : prevLock;
    try {
      synchronized (lock) {
        return supplier.get();
      }
    } finally {
      synchronized (locks) {
        locks.remove(name);
      }
    }
  }
}
