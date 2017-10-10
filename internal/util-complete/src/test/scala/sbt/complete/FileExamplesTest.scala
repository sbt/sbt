/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import java.io.File
import sbt.io.IO._

class FileExamplesTest extends UnitSpec {

  "listing all files in an absolute base directory" should
    "produce the entire base directory's contents" in {
    val _ = new DirectoryStructure {
      fileExamples().toList should contain theSameElementsAs (allRelativizedPaths)
    }
  }

  "listing files with a prefix that matches none" should
    "produce an empty list" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "z") {
      fileExamples().toList shouldBe empty
    }
  }

  "listing single-character prefixed files" should
    "produce matching paths only" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "f") {
      fileExamples().toList should contain theSameElementsAs (prefixedPathsOnly)
    }
  }

  "listing directory-prefixed files" should
    "produce matching paths only" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "far") {
      fileExamples().toList should contain theSameElementsAs (prefixedPathsOnly)
    }
  }

  it should "produce sub-dir contents only when appending a file separator to the directory" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "far" + File.separator) {
      fileExamples().toList should contain theSameElementsAs (prefixedPathsOnly)
    }
  }

  "listing files with a sub-path prefix" should
    "produce matching paths only" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "far" + File.separator + "ba") {
      fileExamples().toList should contain theSameElementsAs (prefixedPathsOnly)
    }
  }

  "completing a full path" should
    "produce a list with an empty string" in {
    val _ = new DirectoryStructure(withCompletionPrefix = "bazaar") {
      fileExamples().toList shouldEqual List("")
    }
  }

  // TODO: Remove DelayedInit - https://github.com/scala/scala/releases/tag/v2.11.0-RC1
  class DirectoryStructure(withCompletionPrefix: String = "") extends DelayedInit {
    var fileExamples: FileExamples = _
    var baseDir: File = _
    var childFiles: List[File] = _
    var childDirectories: List[File] = _
    var nestedFiles: List[File] = _
    var nestedDirectories: List[File] = _

    def allRelativizedPaths: List[String] =
      (childFiles ++ childDirectories ++ nestedFiles ++ nestedDirectories)
        .map(relativize(baseDir, _).get)

    def prefixedPathsOnly: List[String] =
      allRelativizedPaths
        .filter(_ startsWith withCompletionPrefix)
        .map(_ substring withCompletionPrefix.length)

    override def delayedInit(testBody: => Unit): Unit = {
      withTemporaryDirectory { tempDir =>
        createSampleDirStructure(tempDir)
        fileExamples = new FileExamples(baseDir, withCompletionPrefix)
        testBody
      }
    }

    private def createSampleDirStructure(tempDir: File): Unit = {
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
