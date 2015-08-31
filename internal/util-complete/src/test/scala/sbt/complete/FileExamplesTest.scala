package sbt.complete

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import java.io.File
import sbt.io.IO._

class FileExamplesTest extends Specification {

  "listing all files in an absolute base directory" should {
    "produce the entire base directory's contents" in new directoryStructure {
      fileExamples().toList should containTheSameElementsAs(allRelativizedPaths)
    }
  }

  "listing files with a prefix that matches none" should {
    "produce an empty list" in new directoryStructure(withCompletionPrefix = "z") {
      fileExamples().toList should beEmpty
    }
  }

  "listing single-character prefixed files" should {
    "produce matching paths only" in new directoryStructure(withCompletionPrefix = "f") {
      fileExamples().toList should containTheSameElementsAs(prefixedPathsOnly)
    }
  }

  "listing directory-prefixed files" should {
    "produce matching paths only" in new directoryStructure(withCompletionPrefix = "far") {
      fileExamples().toList should containTheSameElementsAs(prefixedPathsOnly)
    }

    "produce sub-dir contents only when appending a file separator to the directory" in new directoryStructure(withCompletionPrefix = "far" + File.separator) {
      fileExamples().toList should containTheSameElementsAs(prefixedPathsOnly)
    }
  }

  "listing files with a sub-path prefix" should {
    "produce matching paths only" in new directoryStructure(withCompletionPrefix = "far" + File.separator + "ba") {
      fileExamples().toList should containTheSameElementsAs(prefixedPathsOnly)
    }
  }

  "completing a full path" should {
    "produce a list with an empty string" in new directoryStructure(withCompletionPrefix = "bazaar") {
      fileExamples().toList shouldEqual List("")
    }
  }

  class directoryStructure(withCompletionPrefix: String = "") extends Scope with DelayedInit {
    var fileExamples: FileExamples = _
    var baseDir: File = _
    var childFiles: List[File] = _
    var childDirectories: List[File] = _
    var nestedFiles: List[File] = _
    var nestedDirectories: List[File] = _

    def allRelativizedPaths: List[String] =
      (childFiles ++ childDirectories ++ nestedFiles ++ nestedDirectories).map(relativize(baseDir, _).get)

    def prefixedPathsOnly: List[String] =
      allRelativizedPaths.filter(_ startsWith withCompletionPrefix).map(_ substring withCompletionPrefix.length)

    override def delayedInit(testBody: => Unit): Unit = {
      withTemporaryDirectory {
        tempDir =>
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

    private def toChildFiles(baseDir: File, files: List[String]): List[File] = files.map(new File(baseDir, _))
  }

}
