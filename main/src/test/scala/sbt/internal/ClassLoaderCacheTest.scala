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
import sbt.io.IO

object ClassLoaderCacheTest {
  private val initLoader = this.getClass.getClassLoader
  implicit class CacheOps(val c: ClassLoaderCache) {
    def get(classpath: Seq[File]): ClassLoader =
      c.get((classpath, initLoader, Map.empty, new File("/dev/null")))
  }
}
class ClassLoaderCacheTest extends FlatSpec with Matchers {
  import ClassLoaderCacheTest._
  def withCache[R](size: Int)(f: CacheOps => R): R = {
    val cache = ClassLoaderCache(size)
    try f(new CacheOps(cache))
    finally cache.close()
  }
  "ClassLoaderCache.get" should "make a new loader when full" in withCache(0) { cache =>
    val classPath = Seq.empty[File]
    val firstLoader = cache.get(classPath)
    val secondLoader = cache.get(classPath)
    assert(firstLoader != secondLoader)
  }
  it should "not make a new loader when it already exists" in withCache(1) { cache =>
    val classPath = Seq.empty[File]
    val firstLoader = cache.get(classPath)
    val secondLoader = cache.get(classPath)
    assert(firstLoader == secondLoader)
  }
  it should "evict loaders" in withCache(2) { cache =>
    val firstClassPath = Seq.empty[File]
    val secondClassPath = new File("foo") :: Nil
    val thirdClassPath = new File("foo") :: new File("bar") :: Nil
    val firstLoader = cache.get(firstClassPath)
    val secondLoader = cache.get(secondClassPath)
    val thirdLoader = cache.get(thirdClassPath)
    assert(cache.get(thirdClassPath) == thirdLoader)
    assert(cache.get(secondClassPath) == secondLoader)
    assert(cache.get(firstClassPath) != firstLoader)
    assert(cache.get(thirdClassPath) != thirdLoader)
  }
  "Snapshots" should "be invalidated" in IO.withTemporaryDirectory { dir =>
    val snapshotJar = Files.createFile(dir.toPath.resolve("foo-SNAPSHOT.jar")).toFile
    val regularJar = Files.createFile(dir.toPath.resolve("regular.jar")).toFile
    withCache(1) { cache =>
      val jarClassPath = snapshotJar :: regularJar :: Nil
      val initLoader = cache.get(jarClassPath)
      IO.setModifiedTimeOrFalse(snapshotJar, System.currentTimeMillis + 5000L)
      val secondLoader = cache.get(jarClassPath)
      assert(initLoader != secondLoader)
      assert(initLoader.getParent == secondLoader.getParent)
      assert(cache.get(jarClassPath) == secondLoader)
      assert(cache.get(jarClassPath) != initLoader)
    }
  }
}
