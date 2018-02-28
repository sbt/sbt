/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import java.io.File
import sbt.io.IO

class FileExamplesTest extends UnitSpec {

  "listing all files in an absolute base directory" should
    "produce the entire base directory's contents" in {
    withDirectoryStructure() { ds =>
      ds.fileExamples().toList should contain theSameElementsAs (ds.allRelativizedPaths)
    }
  }

  "listing files with a prefix that matches none" should "produce an empty list" in {
    withDirectoryStructure(withCompletionPrefix = "z") { ds =>
      ds.fileExamples().toList shouldBe empty
    }
  }

  "listing single-character prefixed files" should "produce matching paths only" in {
    withDirectoryStructure(withCompletionPrefix = "f") { ds =>
      ds.fileExamples().toList should contain theSameElementsAs (ds.prefixedPathsOnly)
    }
  }

  "listing directory-prefixed files" should "produce matching paths only" in {
    withDirectoryStructure(withCompletionPrefix = "far") { ds =>
      ds.fileExamples().toList should contain theSameElementsAs (ds.prefixedPathsOnly)
    }
  }

  it should "produce sub-dir contents only when appending a file separator to the directory" in {
    withDirectoryStructure(withCompletionPrefix = "far" + File.separator) { ds =>
      ds.fileExamples().toList should contain theSameElementsAs (ds.prefixedPathsOnly)
    }
  }

  "listing files with a sub-path prefix" should "produce matching paths only" in {
    withDirectoryStructure(withCompletionPrefix = "far" + File.separator + "ba") { ds =>
      ds.fileExamples().toList should contain theSameElementsAs (ds.prefixedPathsOnly)
    }
  }

  "completing a full path" should "produce a list with an empty string" in {
    withDirectoryStructure(withCompletionPrefix = "bazaar") { ds =>
      ds.fileExamples().toList shouldEqual List("")
    }
  }

  def withDirectoryStructure[A](withCompletionPrefix: String = "")(
      thunk: DirectoryStructure => A): Unit = {
    IO.withTemporaryDirectory { tempDir =>
      val ds = new DirectoryStructure(withCompletionPrefix)
      ds.createSampleDirStructure(tempDir)
      ds.fileExamples = new FileExamples(ds.baseDir, withCompletionPrefix)
      thunk(ds)
    }
  }

  final class DirectoryStructure(withCompletionPrefix: String) {
    var fileExamples: FileExamples = _
    var baseDir: File = _
    var childFiles: List[File] = _
    var childDirectories: List[File] = _
    var nestedFiles: List[File] = _
    var nestedDirectories: List[File] = _

    def allRelativizedPaths: List[String] =
      (childFiles ++ childDirectories ++ nestedFiles ++ nestedDirectories)
        .map(IO.relativize(baseDir, _).get)

    def prefixedPathsOnly: List[String] =
      allRelativizedPaths
        .filter(_ startsWith withCompletionPrefix)
        .map(_ substring withCompletionPrefix.length)

    def createSampleDirStructure(tempDir: File): Unit = {
      childFiles = toChildFiles(tempDir, List("foo", "bar", "bazaar"))
      childDirectories = toChildFiles(tempDir, List("moo", "far"))
      nestedFiles = toChildFiles(childDirectories(1), List("farfile1", "barfile2"))
      nestedDirectories = toChildFiles(childDirectories(1), List("fardir1", "bardir2"))

      (childDirectories ++ nestedDirectories).map(_.mkdirs())
      (childFiles ++ nestedFiles).map(_.createNewFile())

      baseDir = tempDir
    }

    private def toChildFiles(baseDir: File, files: List[String]): List[File] =
      files.map(new File(baseDir, _))
  }

}
