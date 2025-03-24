/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.nio.file.Files

import sbt.internal.inc.MappedFileConverter
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.FileConverter

object MapperTest extends verify.BasicTestSuite:
  test("directory should create mappings including the baseDirectory") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val nestedFile1 = tempDirectory / "file1"
      val nestedFile2 = tempDirectory / "file2"
      val nestedDir = tempDirectory / "dir1"
      val nestedDirFile = nestedDir / "dir1-file1"

      IO.touch(nestedFile1)
      IO.touch(nestedFile2)
      IO.createDirectory(nestedDir)
      IO.touch(nestedDirFile)

      val mappings = Mapper
        .directory(tempDirectory)
        .map { (h, p) =>
          (h.toString, p)
        }

      Predef.assert(
        mappings.toSet == List(
          "${BASE}/" -> s"${tempDirectory.getName}",
          "${BASE}/file1" -> s"${tempDirectory.getName}/file1",
          "${BASE}/file2" -> s"${tempDirectory.getName}/file2",
          "${BASE}/dir1" -> s"${tempDirectory.getName}/dir1",
          "${BASE}/dir1/dir1-file1" -> s"${tempDirectory.getName}/dir1/dir1-file1",
        ).toSet
      )
  }

  test("it should create one mapping entry for an empty directory") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val mappings = Mapper
        .directory(tempDirectory)
        .map { (h, p) =>
          (h.toString, p)
        }
      Predef.assert(
        mappings.toSet == List(
          "${BASE}/" -> "foo"
        ).toSet,
        s"found $mappings"
      )
  }

  test("it should create an empty mappings sequence for a non-existing directory") {
    withTempDirectory: tempDirectory =>
      val conv0: FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      given FileConverter = conv0
      val nonExistingDirectory = tempDirectory / "imaginary"
      val mappings = Mapper.directory(nonExistingDirectory)
      assert(mappings.isEmpty)
  }

  test("it should create one mapping entry if the directory is a file") {
    withTempDirectory: tempDirectory =>
      val conv0: FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      given FileConverter = conv0
      val file = tempDirectory / "file"
      IO.touch(file)
      val mappings = Mapper.directory(file).map { (h, p) =>
        (h.toString, p)
      }
      Predef.assert(
        mappings.toSet == Set("${BASE}/file" -> s"${file.getName}"),
        s"actual: $mappings"
      )
  }

  test("contentOf should create mappings excluding the baseDirectory") {
    withTempDirectory: tempDirectory =>
      val conv0: FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      given FileConverter = conv0
      val nestedFile1 = tempDirectory / "file1"
      val nestedFile2 = tempDirectory / "file2"
      val nestedDir = tempDirectory / "dir1"
      val nestedDirFile = nestedDir / "dir1-file1"
      IO.touch(nestedFile1)
      IO.touch(nestedFile2)
      IO.createDirectory(nestedDir)
      IO.touch(nestedDirFile)

      val mappings = Mapper.contentOf(tempDirectory).map { (h, p) =>
        (h.toString, p)
      }
      Predef.assert(
        mappings.toSet == List(
          "${BASE}/file1" -> "file1",
          "${BASE}/file2" -> "file2",
          "${BASE}/dir1" -> "dir1",
          "${BASE}/dir1/dir1-file1" -> "dir1/dir1-file1",
        ).toSet,
        s"actual: $mappings"
      )
  }

  test("it should create an empty mappings sequence for an empty directory") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val mappings = Mapper.contentOf(tempDirectory)
      assert(mappings.isEmpty)
  }

  test("it should create an empty mappings sequence for a non-existing directory") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val nonExistingDirectory = tempDirectory / "imaginary"
      val mappings = Mapper.contentOf(nonExistingDirectory)
      assert(mappings.isEmpty)
  }

  test("it should create an empty mappings sequence if the directory is a file") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val file = tempDirectory / "file"
      val mappings = Mapper.contentOf(file)
      assert(mappings.isEmpty)
  }

  test("it should create an empty mappings sequence if the directory is a file") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val file = tempDirectory / "file"
      val mappings = Mapper.contentOf(file)
      assert(mappings.isEmpty)
  }

  test("allSubpaths should not include the base directory") {
    withTempDirectory: tempDirectory =>
      given FileConverter = MappedFileConverter(Map("BASE" -> tempDirectory.toPath()), true)
      val file = Files.createFile((tempDirectory / "file").toPath)
      val paths = Mapper.allSubpaths(tempDirectory).toVector.map(_._1.toString).toSet
      assert(paths.contains("${BASE}/file"))
      assert(!paths.contains("${BASE}"))
  }

  def withTempDirectory[A1](f: File => A1): A1 =
    IO.withTemporaryDirectory: tempDirectory0 =>
      val tempDirectory = tempDirectory0 / "foo"
      IO.createDirectory(tempDirectory)
      f(tempDirectory)
end MapperTest
