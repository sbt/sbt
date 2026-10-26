/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import java.io.File

import hedgehog.*
import hedgehog.runner.*
import _root_.sbt.io.IO

object FileExamplesTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "listing all files in an absolute base directory should produce the entire base directory's contents",
      withDirectoryStructure()(ds => sameElements(ds.fileExamples().toList, ds.allRelativizedPaths))
    ),
    example(
      "listing files with a prefix that matches none should produce an empty list",
      withDirectoryStructure(withCompletionPrefix = "z")(ds => ds.fileExamples().toList ==== Nil)
    ),
    example(
      "listing single-character prefixed files should produce matching paths only",
      withDirectoryStructure(withCompletionPrefix = "f")(ds =>
        sameElements(ds.fileExamples().toList, ds.prefixedPathsOnly)
      )
    ),
    example(
      "listing directory-prefixed files should produce matching paths only",
      withDirectoryStructure(withCompletionPrefix = "far")(ds =>
        sameElements(ds.fileExamples().toList, ds.prefixedPathsOnly)
      )
    ),
    example(
      "listing directory-prefixed files should produce sub-dir contents only when appending a file separator to the directory",
      withDirectoryStructure(withCompletionPrefix = "far" + File.separator)(ds =>
        sameElements(ds.fileExamples().toList, ds.prefixedPathsOnly)
      )
    ),
    example(
      "listing files with a sub-path prefix should produce matching paths only",
      withDirectoryStructure(withCompletionPrefix = "far" + File.separator + "ba")(ds =>
        sameElements(ds.fileExamples().toList, ds.prefixedPathsOnly)
      )
    ),
    example(
      "completing a full path should produce a list with an empty string",
      withDirectoryStructure(withCompletionPrefix = "bazaar")(ds =>
        ds.fileExamples().toList ==== List("")
      )
    ),
  )

  def sameElements(actual: List[String], expected: List[String]): Result =
    actual.sorted ==== expected.sorted

  def withDirectoryStructure(withCompletionPrefix: String = "")(
      thunk: DirectoryStructure => Result
  ): Result =
    IO.withTemporaryDirectory: tempDir =>
      val ds = new DirectoryStructure(withCompletionPrefix)
      ds.createSampleDirStructure(tempDir)
      ds.fileExamples = new FileExamples(ds.baseDir, withCompletionPrefix)
      thunk(ds)

  final class DirectoryStructure(withCompletionPrefix: String):
    var fileExamples: FileExamples = scala.compiletime.uninitialized
    var baseDir: File = scala.compiletime.uninitialized
    var childFiles: List[File] = scala.compiletime.uninitialized
    var childDirectories: List[File] = scala.compiletime.uninitialized
    var nestedFiles: List[File] = scala.compiletime.uninitialized
    var nestedDirectories: List[File] = scala.compiletime.uninitialized

    def allRelativizedPaths: List[String] =
      (childFiles ++ childDirectories ++ nestedFiles ++ nestedDirectories)
        .map(IO.relativize(baseDir, _).get)

    def prefixedPathsOnly: List[String] =
      allRelativizedPaths
        .withFilter(_.startsWith(withCompletionPrefix))
        .map(_.substring(withCompletionPrefix.length))

    def createSampleDirStructure(tempDir: File): Unit =
      childFiles = toChildFiles(tempDir, List("foo", "bar", "bazaar"))
      childDirectories = toChildFiles(tempDir, List("moo", "far"))
      nestedFiles = toChildFiles(childDirectories(1), List("farfile1", "barfile2"))
      nestedDirectories = toChildFiles(childDirectories(1), List("fardir1", "bardir2"))

      (childDirectories ++ nestedDirectories).foreach(_.mkdirs())
      (childFiles ++ nestedFiles).foreach(_.createNewFile())

      baseDir = tempDir

    private def toChildFiles(baseDir: File, files: List[String]): List[File] =
      files.map(new File(baseDir, _))
  end DirectoryStructure
end FileExamplesTest
