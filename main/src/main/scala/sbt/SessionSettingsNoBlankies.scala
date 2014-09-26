package sbt

import java.io.File

import scala.collection.immutable.SortedMap
import scala.reflect.runtime.universe._

object SessionSettingsNoBlankies {

  private val FAKE_FILE = new File("fake")
  val REVERSE_ORDERING_INT = Ordering[Int].reverse

  def oldLinesToNew(content: List[String], lineMap: SortedMap[Int, List[(Int, List[String])]]): List[String] =
    if (lineMap.isEmpty) {
      content
    } else {
      val head = lineMap.head
      val newContent = toNewContent(content, head)
      oldLinesToNew(newContent, lineMap.tail)
    }

  private def toNewContent(content: List[String], setCommands: (Int, List[(Int, List[String])])): List[String] = {
    val (from, newSettings) = setCommands

    val newTreeStringsMap = newSettings.map {
      case (_, lines) => toTreeStringMap(lines)
    }
    val to = newSettings.map(_._1).max
    val originalLine = content.slice(from - 1, to - 1)

    val operations = newTreeStringsMap.flatMap {
      map =>
        map.flatMap {
          case (name, (startIndex, statement)) =>
            val validLines = cutExpression(originalLine, name)
            val treeStringMap = toTreeStringMap(validLines)
            treeStringMap.get(name).map {
              case (t, oldContent) =>
                (startIndex, oldContent, statement)
            }
        }
    }
    val statements = XmlContent.handleXmlContent(originalLine.mkString("\n"))
    val sortedOperations = operations.sortBy(_._1)(REVERSE_ORDERING_INT)
    val newContent = sortedOperations.foldLeft(statements) {
      case (acc, (startIndex, old, newStatement)) =>
        acc.replace(old, newStatement)
    }
    val newLines = newContent.lines.toList
    content.take(from - 1) ++ newLines ++ content.drop(to - 1)
  }

  private[sbt] def cutExpression(l: List[String], name: String): List[String] = l match {
    case h +: t =>
      val statements = SplitExpressionsNoBlankies(FAKE_FILE, l).settingsTrees
      val lastIndex = statements.lastIndexWhere {
        tuple => extractSettingName(tuple._2) == name
      }
      val (statement, tree) = statements(lastIndex)

      if (tree.pos.end >= h.length) {
        l
      } else {
        statement +: t
      }
    case _ =>
      l
  }

  private def toTreeStringMap(lines: List[String]) = {
    val split = SplitExpressionsNoBlankies(FAKE_FILE, lines)
    val trees = split.settingsTrees
    val seq = trees.map {
      case (statement, tree) =>
        (extractSettingName(tree), (tree.pos.start, statement))
    }
    seq.toMap
  }

  private def extractSettingName(tree: Tree): String = {
    tree.children match {
      case h :: _ =>
        extractSettingName(h)
      case _ =>
        tree.toString()
    }
  }

}
