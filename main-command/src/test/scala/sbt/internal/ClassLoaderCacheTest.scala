/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.io.File
import java.nio.file.Files

import sbt.internal.classpath.ClassLoaderCache
import sbt.io.IO
import verify.BasicTestSuite
import scala.util.Using

object ClassLoaderCacheTest extends BasicTestSuite:

  extension (c: ClassLoaderCache) def get(classpath: Seq[File]): ClassLoader = c(classpath.toList)

  private def withCache[R](f: ClassLoaderCache => R): R =
    Using.resource(new ClassLoaderCache(ClassLoader.getSystemClassLoader))(f)

  test("ClassLoaderCache should make a new loader when cleared"):
    withCache: cache =>
      val classPath = Seq.empty[File]
      val firstLoader = cache.get(classPath)
      cache.clear()
      val secondLoader = cache.get(classPath)
      assert(firstLoader != secondLoader)

  test("ClassLoaderCache should reuse loader for same classpath"):
    withCache: cache =>
      val classPath = Seq.empty[File]
      val firstLoader = cache.get(classPath)
      val secondLoader = cache.get(classPath)
      assert(firstLoader == secondLoader)

  test("Snapshots should be invalidated when modified"):
    IO.withTemporaryDirectory: dir =>
      val snapshotJar = Files.createFile(dir.toPath.resolve("foo-SNAPSHOT.jar")).toFile
      val regularJar = Files.createFile(dir.toPath.resolve("regular.jar")).toFile
      withCache: cache =>
        val jarClassPath = snapshotJar :: regularJar :: Nil
        val initLoader = cache.get(jarClassPath)
        IO.setModifiedTimeOrFalse(snapshotJar, System.currentTimeMillis + 5000L)
        val secondLoader = cache.get(jarClassPath)
        Predef.assert(initLoader != secondLoader)
        Predef.assert(cache.get(jarClassPath) == secondLoader)
        Predef.assert(cache.get(jarClassPath) != initLoader)

end ClassLoaderCacheTest
