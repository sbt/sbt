/*
 * sbt IO
 * Copyright Scala Center, Lightbend, and Mark Harrah
 *
 * Licensed under Apache License 2.0
 * SPDX-License-Identifier: Apache-2.0
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package sbt

import java.nio.file.{ Files, Path => NioPath }

import org.scalatest.Outcome
import org.scalatest.flatspec
import org.scalatest.matchers.should.Matchers
import sbt.io.IO
import sbt.io.syntax._

class MapperSpec extends flatspec.FixtureAnyFlatSpec with Matchers {

  type FixtureParam = NioPath

  "directory" should "create mappings including the baseDirectory" in { tempDirectory =>
    val nestedFile1 = Files.createFile(tempDirectory.resolve("file1")).toFile
    val nestedFile2 = Files.createFile(tempDirectory.resolve("file2")).toFile
    val nestedDir = Files.createDirectory(tempDirectory.resolve("dir1"))
    val nestedDirFile = Files.createDirectory(nestedDir.resolve("dir1-file1")).toFile

    IO.touch(nestedFile1)
    IO.touch(nestedFile2)
    IO.createDirectory(nestedDir.toFile)
    IO.touch(nestedDirFile)

    val mappings = Mapper.directory(tempDirectory.toFile).map { case (f, s) => (f, file(s)) }

    mappings should contain theSameElementsAs List(
      tempDirectory.toFile -> file(s"${tempDirectory.getFileName}"),
      nestedFile1 -> file(s"${tempDirectory.getFileName}/file1"),
      nestedFile2 -> file(s"${tempDirectory.getFileName}/file2"),
      nestedDir.toFile -> file(s"${tempDirectory.getFileName}/dir1"),
      nestedDirFile -> file(s"${tempDirectory.getFileName}/dir1/dir1-file1")
    )
  }

  it should "create one mapping entry for an empty directory" in { tempDirectory =>
    val mappings = Mapper.directory(tempDirectory.toFile)

    mappings should contain theSameElementsAs List[(File, String)](
      tempDirectory.toFile -> s"${tempDirectory.getFileName}"
    )
  }

  it should "create an empty mappings sequence for a non-existing directory" in { tempDirectory =>
    val nonExistingDirectory = tempDirectory.resolve("imaginary")
    val mappings = Mapper.directory(nonExistingDirectory.toFile)

    mappings should be(empty)
  }

  it should "create one mapping entry if the directory is a file" in { tempDirectory =>
    val file = tempDirectory.resolve("file").toFile
    IO.touch(file)
    val mappings = Mapper.directory(file)

    mappings should contain theSameElementsAs List[(File, String)](
      file -> s"${file.getName}"
    )
  }

  "contentOf" should "create mappings excluding the baseDirectory" in { tempDirectory =>
    val nestedFile1 = Files.createFile(tempDirectory.resolve("file1")).toFile
    val nestedFile2 = Files.createFile(tempDirectory.resolve("file2")).toFile
    val nestedDir = Files.createDirectory(tempDirectory.resolve("dir1"))
    val nestedDirFile = Files.createDirectory(nestedDir.resolve("dir1-file1")).toFile

    IO.touch(nestedFile1)
    IO.touch(nestedFile2)
    IO.createDirectory(nestedDir.toFile)
    IO.touch(nestedDirFile)

    val mappings = Mapper.contentOf(tempDirectory.toFile).map { case (f, s) => (f, file(s)) }

    mappings should contain theSameElementsAs List(
      nestedFile1 -> file(s"file1"),
      nestedFile2 -> file(s"file2"),
      nestedDir.toFile -> file(s"dir1"),
      nestedDirFile -> file(s"dir1/dir1-file1")
    )
  }

  it should "create an empty mappings sequence for an empty directory" in { tempDirectory =>
    val mappings = Mapper.contentOf(tempDirectory.toFile)

    mappings should be(empty)
  }

  it should "create an empty mappings sequence for a non-existing directory" in { tempDirectory =>
    val nonExistingDirectory = tempDirectory.resolve("imaginary")
    val mappings = Mapper.contentOf(nonExistingDirectory.toFile)

    mappings should be(empty)
  }

  it should "create an empty mappings sequence if the directory is a file" in { tempDirectory =>
    val file = tempDirectory.resolve("file").toFile
    val mappings = Mapper.contentOf(file)

    mappings should be(empty)
  }

  "allSubpaths" should "not include the base directory" in { tempDirectory =>
    val file = Files.createFile(tempDirectory.resolve("file"))
    val paths = Mapper.allSubpaths(tempDirectory.toFile).toVector.map(_._1.toPath).toSet
    assert(paths.contains(file))
    assert(!paths.contains(tempDirectory))
  }

  override protected def withFixture(test: OneArgTest): Outcome = {
    val tmpDir = Files.createTempDirectory("mappings")
    try {
      withFixture(test.toNoArgTest(tmpDir))
    } finally {
      // cleanup an delete the temp directory
      IO.delete(tmpDir.toFile)
    }
  }
}
