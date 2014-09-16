package sbt

import java.io.{ File, FilenameFilter }

import org.specs2.matcher.MatchResult

import scala.collection.GenTraversableOnce
import scala.collection.immutable.{ SortedMap, TreeMap }
import scala.io.Source
import scala.xml.XML

abstract class AbstractSessionSettingsSpec(folder: String, printDetails: Boolean = false) extends AbstractSpec {
  protected val rootPath = getClass.getResource("").getPath + folder
  println(s"Reading files from: $rootPath")
  protected val rootDir = new File(rootPath)

  "SessionSettings " should {
    "Be identical for empty map " in {
      def unit(f: File) = Seq((Source.fromFile(f).getLines().toList, SortedMap.empty[Int, List[(Int, List[String])]]))
      runTestOnFiles(unit)
    }

    "Replace statements " in {
      runTestOnFiles(replace)
    }
  }

  private def runTestOnFiles(expectedResultAndMap: File => Seq[(List[String], SortedMap[Int, List[(Int, List[String])]])]): MatchResult[GenTraversableOnce[File]] = {

    val allFiles = rootDir.listFiles(new FilenameFilter() {
      def accept(dir: File, name: String) = name.endsWith(".sbt.txt")
    }).toList
    foreach(allFiles) {
      file =>
        val originalLines = Source.fromFile(file).getLines().toList
        foreach(expectedResultAndMap(file)) {
          case (expectedResultList, map) =>
            val resultList = SessionSettingsNoBlankies.oldLinesToNew(originalLines, map)
            val expected = SplitExpressionsNoBlankies(file, expectedResultList)
            val result = SplitExpressionsNoBlankies(file, resultList)
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
    }).toList
    dirs.flatMap {
      dir =>
        val files = dir.listFiles(new FilenameFilter {
          override def accept(dir: File, name: String) = name.endsWith(".xml")
        })
        files.map { xmlFile =>
          val xml = XML.loadFile(xmlFile)
          val result = Source.fromFile(xmlFile.getAbsolutePath + ".result").getLines().toList
          val tupleCollection = (xml \\ "settings" \\ "setting").map {
            node =>
              val set = (node \\ "set").text
              val start = (node \\ "start").text.toInt
              val end = (node \\ "end").text.toInt
              (start, (end, List(set)))
          }.toList
          val map = tupleCollection.groupBy(el => el._1).map {
            case (k, seq) => (k, seq.map(el => el._2))
          }
          (result, TreeMap(map.toArray: _*)(SessionSettingsNoBlankies.REVERSE_ORDERING_INT))
        }
    }
  }

}

class SessionSettingsSpec extends AbstractSessionSettingsSpec("../session-settings")