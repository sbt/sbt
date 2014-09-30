package sbt.internals.parser

import scala.reflect.runtime.universe._

private[sbt] object SbtRefactorings {

  import sbt.internals.parser.SplitExpressionsNoBlankies.{ END_OF_LINE, FAKE_FILE }
  val EMPTY_STRING = ""
  val REVERSE_ORDERING_INT = Ordering[Int].reverse

  def applyStatements(lines: List[String], setCommands: List[List[String]]): List[String] = {
    val split = SplitExpressionsNoBlankies(FAKE_FILE, lines)
    val recordedCommand = setCommands.flatMap {
      command =>
        val map = toTreeStringMap(command)
        map.flatMap {
          case (name, (startPos, statement)) =>
            split.settingsTrees.foldLeft(Seq.empty[(Int, String, String)]) {
              case (acc, (statement, tree)) =>
                val treeName = extractSettingName(tree)
                if (name == treeName) {
                  val replacement = if (acc.isEmpty) {
                    command.mkString(END_OF_LINE)
                  } else {
                    EMPTY_STRING
                  }
                  (tree.pos.start, statement, replacement) +: acc
                } else {
                  acc
                }
            }
        }
    }
    val sortedRecordedCommand = recordedCommand.sortBy(_._1)(REVERSE_ORDERING_INT)

    val newContent = sortedRecordedCommand.foldLeft(split.modifiedContent) {
      case (acc, (from, old, replacement)) =>
        val before = acc.substring(0, from)
        val after = acc.substring(from + old.length, acc.length)
        before + replacement + after
    }
    newContent.lines.toList
  }

  private def toTreeStringMap(command: List[String]) = {
    val split = SplitExpressionsNoBlankies(FAKE_FILE, command)
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
