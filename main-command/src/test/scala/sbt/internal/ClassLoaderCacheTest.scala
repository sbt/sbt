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

  test("Loaders evicted by a newer timestamp should be closed by clear()"):
    IO.withTemporaryDirectory: dir =>
      val entry = "leak-test-resource.txt"
      val jar = dir.toPath.resolve("evicted.jar").toFile
      Using.resource(new java.util.jar.JarOutputStream(new java.io.FileOutputStream(jar))): out =>
        out.putNextEntry(new java.util.zip.ZipEntry(entry))
        out.write("hello".getBytes("UTF-8"))
        out.closeEntry()

      withCache: cache =>
        val classPath = jar :: Nil
        val first = cache.get(classPath)
        Predef.assert(first.getResource(entry) != null, "jar resource should load before eviction")

        // Bumping the timestamp makes a new Key, and addLoader calls clearExpiredLoaders after
        // inserting it, so `first`'s entry is evicted from the delegate map here. Once evicted
        // it is unreachable from the map, so only the `retired` set can still close it.
        IO.setModifiedTimeOrFalse(jar, System.currentTimeMillis + 5000L)
        val second = cache.get(classPath)
        Predef.assert(first != second, "a newer timestamp should produce a new loader")

        cache.clear()
        Predef.assert(
          first.getResource(entry) == null,
          "clear() should have closed the evicted loader, releasing its jar handle"
        )

end ClassLoaderCacheTest
