package sbt.internals.parser

import java.io.{ File, FilenameFilter }

import org.specs2.matcher.MatchResult

import scala.collection.GenTraversableOnce
import scala.io.Source

abstract class AbstractSessionSettingsSpec(folder: String, deepCompare: Boolean = false) extends AbstractSpec {
  protected val rootPath = getClass.getClassLoader.getResource("").getPath + folder
  println(s"Reading files from: $rootPath")
  protected val rootDir = new File(rootPath)

  "SessionSettings " should {
    "Be identical for empty map " in {
      def unit(f: File) = Seq((Source.fromFile(f).getLines().toList, Nil))
      runTestOnFiles(unit)
    }

    "Replace statements " in {
      runTestOnFiles(replace)
    }
  }

  private def runTestOnFiles(expectedResultAndMap: File => Seq[(List[String], List[String])]): MatchResult[GenTraversableOnce[File]] = {

    val allFiles = rootDir.listFiles(new FilenameFilter() {
      def accept(dir: File, name: String) = name.endsWith(".sbt.txt")
    }).toList
    foreach(allFiles) {
      file =>
        val originalLines = Source.fromFile(file).getLines().toList
        foreach(expectedResultAndMap(file)) {
          case (expectedResultList, commands) =>
            val resultList = SbtRefactorings.applyStatements(originalLines, commands.map(List(_)))
            val expected = SplitExpressionsNoBlankies(file, expectedResultList)
            val result = SplitExpressionsNoBlankies(file, resultList)
            if (deepCompare) {
              expectedResultList must_== resultList
            }
            result.settings must_== expected.settings

        }
    }
  }

  protected def replace(f: File) = {
    val dirs = rootDir.listFiles(new FilenameFilter() {
      def accept(dir: File, name: String) = {
        val startsWith = f.getName + "_"
        name.startsWith(startsWith)
      }
    }).toSeq
    dirs.flatMap {
      dir =>
        val files = dir.listFiles(new FilenameFilter {
          override def accept(dir: File, name: String) = name.endsWith(".set")
        })
        files.map { file =>
          val list = Source.fromFile(file).getLines().toList
          val result = Source.fromFile(file.getAbsolutePath + ".result").getLines().toList
          (result, list)
        }
    }
  }

}

class SessionSettingsSpec extends AbstractSessionSettingsSpec("session-settings")

//class SessionSettingsQuickSpec extends AbstractSessionSettingsSpec("session-settings-quick", true)