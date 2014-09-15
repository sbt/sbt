package sbt

import java.io.File

import scala.collection.immutable.SortedMap
import scala.reflect.runtime.universe._

object SessionSettingsNoBlankies {

  val REVERSE_ORDERING_INT = Ordering[Int].reverse

  def oldLinesToNew(content: List[String], lineMap: SortedMap[Int, List[(Int, List[String])]]): List[String] =
    if (lineMap.isEmpty) {
      content
    } else {
      val head = lineMap.head
      val newContent = toNewContent(content, head)
      oldLinesToNew(newContent, lineMap.tail)
    }

  private def toNewContent(content: List[String], tuple: (Int, List[(Int, List[String])])): List[String] = {
    val (from, newSettingSeq) = tuple

    val newTreeStringSeqMap = newSettingSeq.seq.map {
      case (_, lines) => toTreeStringMap(lines)
    }
    val to = newSettingSeq.map(_._1).max
    val originalLine = content.slice(from - 1, to - 1)

    val operations = newTreeStringSeqMap.flatMap {
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
    val statements = originalLine.mkString("\n")
    val sortedOperations = operations.sortBy(_._1)(REVERSE_ORDERING_INT)
    val newContent = sortedOperations.foldLeft(statements) {
      case (acc, (startIndex, old, newStatement)) =>
        acc.replace(old, newStatement)
    }
    val newLines = newContent.lines.toList
    content.take(from - 1) ++ newLines ++ content.drop(to - 1)
  }

  private def cutExpression(l: List[String], name: String): List[String] = l match {
    case h +: t =>
      val array = h.split(";").filter(_.contains(name))
      array.mkString(";") +: t
    case _ =>
      l
  }

  private def toTreeStringMap(lines: List[String]) = {

    val trees = SplitExpressionsNoBlankies(new File("fake"), lines).settingsTrees
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
