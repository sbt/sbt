/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.nio.file.Files

import org.scalatest.{ FlatSpec, Matchers }
import sbt.internal.classpath.ClassLoaderCache
import sbt.io.IO

object ClassLoaderCacheTest {
  implicit class CacheOps(val c: ClassLoaderCache) {
    def get(classpath: Seq[File]): ClassLoader = c(classpath.toList)
  }
}
class ClassLoaderCacheTest extends FlatSpec with Matchers {
  import ClassLoaderCacheTest._
  private def withCache[R](f: ClassLoaderCache => R): R = {
    val cache = new ClassLoaderCache(ClassLoader.getSystemClassLoader)
    try f(cache)
    finally cache.close()
  }
  "ClassLoaderCache" should "make a new loader when full" in withCache { cache =>
    val classPath = Seq.empty[File]
    val firstLoader = cache.get(classPath)
    cache.clear()
    val secondLoader = cache.get(classPath)
    assert(firstLoader != secondLoader)
  }
  it should "not make a new loader when it already exists" in withCache { cache =>
    val classPath = Seq.empty[File]
    val firstLoader = cache.get(classPath)
    val secondLoader = cache.get(classPath)
    assert(firstLoader == secondLoader)
  }
  "Snapshots" should "be invalidated" in IO.withTemporaryDirectory { dir =>
    val snapshotJar = Files.createFile(dir.toPath.resolve("foo-SNAPSHOT.jar")).toFile
    val regularJar = Files.createFile(dir.toPath.resolve("regular.jar")).toFile
    withCache { cache =>
      val jarClassPath = snapshotJar :: regularJar :: Nil
      val initLoader = cache.get(jarClassPath)
      IO.setModifiedTimeOrFalse(snapshotJar, System.currentTimeMillis + 5000L)
      val secondLoader = cache.get(jarClassPath)
      assert(initLoader != secondLoader)
      assert(cache.get(jarClassPath) == secondLoader)
      assert(cache.get(jarClassPath) != initLoader)
    }
  }
}
