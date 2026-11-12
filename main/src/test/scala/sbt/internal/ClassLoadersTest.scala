/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.{ sbt as _, * }
import hedgehog.runner.*
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{ Files, FileSystemException, Path }
import sbt.io.IO
import sbt.util.Digest

object ClassLoadersTest extends Properties:
  override def tests: List[Test] = List(
    example("CAS-backed archives have stable aliases by content and name", archiveAliases),
    example("ordinary classpath entries retain their paths", ordinaryClasspathEntries),
  )

  private def archiveAliases: Result =
    IO.withTemporaryDirectory: dir =>
      val base = dir.toPath.toRealPath()
      val cas = Files.createDirectory(base.resolve("cas"))
      def blob(content: String): Path =
        val bytes = content.getBytes(UTF_8)
        val digest = Digest.sha256Hash(bytes)
        Files.write(cas.resolve(s"${digest.contentHashStr}-${digest.sizeBytes}"), bytes)
      val oldBlob = blob("old")
      val newBlob = blob("new")
      Files.setLastModifiedTime(newBlob, Files.getLastModifiedTime(oldBlob))
      val jar = base.resolve("core_2.13.jar")
      if trySymlink(jar, base.relativize(oldBlob)) then
        val oldAlias = ClassLoaders.classLoaderFile(jar.toFile)
        val aliasRoot = base.resolve("cas-classloader")
        assert(oldAlias.toPath == aliasRoot.resolve(oldBlob.getFileName).resolve(jar.getFileName))
        assert(ClassLoaders.classLoaderFile(jar.toFile) == oldAlias)
        assert(ClassLoaders.classLoaderFile(oldAlias) == oldAlias)
        assert(Files.isSymbolicLink(oldAlias.toPath) || !Files.isSameFile(oldAlias.toPath, oldBlob))
        assert(new File(ClassLoaders.toRealURL(oldAlias).toURI) == oldAlias)

        Files.delete(jar)
        Files.createSymbolicLink(jar, newBlob)
        val newAlias = ClassLoaders.classLoaderFile(jar.toFile)
        assert(newAlias.toPath == aliasRoot.resolve(newBlob.getFileName).resolve(jar.getFileName))
        assert(newAlias != oldAlias)
        assert(Files.readString(oldAlias.toPath) == "old")
        assert(Files.readString(newAlias.toPath) == "new")

        val zip = Files.createSymbolicLink(base.resolve("core.ZIP"), jar)
        val zipAlias = ClassLoaders.classLoaderFile(zip.toFile)
        assert(zipAlias.toPath == aliasRoot.resolve(newBlob.getFileName).resolve(zip.getFileName))
        assert(Files.readString(zipAlias.toPath) == "new")

        val cache = Files.createDirectory(base.resolve("other-cache"))
        val physicalCas = Files.createDirectory(base.resolve("relocated-blobs"))
        Files.copy(newBlob, physicalCas.resolve(newBlob.getFileName))
        val logicalCas = Files.createSymbolicLink(cache.resolve("cas"), physicalCas)
        val relocatedBlob = logicalCas.resolve(newBlob.getFileName)
        val relocatedJar = base.resolve("relocated.jar")
        for target <- Seq(relocatedBlob, base.relativize(relocatedBlob)) do
          Files.deleteIfExists(relocatedJar)
          Files.createSymbolicLink(relocatedJar, target)
          val alias = ClassLoaders.classLoaderFile(relocatedJar.toFile)
          val expected = cache.resolve("cas-classloader").resolve(newBlob.getFileName)
          assert(alias.toPath == expected.resolve(relocatedJar.getFileName))
          assert(Files.readString(alias.toPath) == "new")
          assert(new File(ClassLoaders.toRealURL(alias).toURI) == alias)
      end if
      Result.success

  private def ordinaryClasspathEntries: Result =
    IO.withTemporaryDirectory: dir =>
      val base = dir.toPath
      val jar = Files.createFile(base.resolve("plain.jar"))
      val classes = Files.createDirectory(base.resolve("classes"))
      assert(ClassLoaders.classLoaderFile(jar.toFile) == jar.toFile)
      assert(ClassLoaders.classLoaderFile(classes.toFile) == classes.toFile)

      val link = base.resolve("plain-link.jar")
      if trySymlink(link, jar) then
        assert(ClassLoaders.classLoaderFile(link.toFile) == link.toFile)
        assert(new File(ClassLoaders.toRealURL(link.toFile).toURI) == jar.toRealPath().toFile)
      Result.success

  private def trySymlink(link: Path, target: Path): Boolean =
    try
      Files.createSymbolicLink(link, target)
      true
    catch
      case _: UnsupportedOperationException                      => false
      case _: FileSystemException if scala.util.Properties.isWin => false
end ClassLoadersTest
