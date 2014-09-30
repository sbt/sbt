package sbt
package internals
package parser

import scala.reflect.runtime.universe._

private[sbt] object SbtRefactorings {

  import sbt.internals.parser.SplitExpressionsNoBlankies.{ END_OF_LINE, FAKE_FILE }
  import sbt.SessionSettings.{ SessionSetting, SbtConfigFile }

  val EMPTY_STRING = ""
  val REVERSE_ORDERING_INT = Ordering[Int].reverse

  /**
   * Refactoring a `.sbt` file so that the new settings are used instead of any existing settings.
   * @param configFile SbtConfigFile with the lines of an sbt file as a List[String] where each string is one line
   * @param commands A List of settings (space separate) that should be inserted into the current file.
   *                 If the settings replaces a value, it will replace the original line in the .sbt file.
   *                 If in the `.sbt` file we have multiply value for one settings -
   *                 the first will be replaced and the other will be removed.
   * @return a SbtConfigFile with new lines which represent the contents of the refactored .sbt file.
   */
  def applySessionSettings(configFile: SbtConfigFile, commands: Seq[SessionSetting]): SbtConfigFile = {
    val (file, lines) = configFile
    val split = SplitExpressionsNoBlankies(FAKE_FILE, lines)
    val recordedCommands = recordCommands(commands, split)
    val sortedRecordedCommands = recordedCommands.sortBy(_._1)(REVERSE_ORDERING_INT)

    val newContent = replaceFromBottomToTop(split.modifiedContent, sortedRecordedCommands)
    (file, newContent.lines.toList)
  }

  private def replaceFromBottomToTop(modifiedContent: String, sortedRecordedCommands: Seq[(Int, String, String)]) = {
    sortedRecordedCommands.foldLeft(modifiedContent) {
      case (acc, (from, old, replacement)) =>
        val before = acc.substring(0, from)
        val after = acc.substring(from + old.length, acc.length)
        val afterLast = emptyStringForEmptyString(after)
        before + replacement + afterLast
    }
  }

  private def emptyStringForEmptyString(text: String) = {
    val trimmed = text.trim
    if (trimmed.isEmpty) trimmed else text
  }

  private def recordCommands(commands: Seq[SessionSetting], split: SplitExpressionsNoBlankies) =
    commands.flatMap {
      case (_, command) =>
        val map = toTreeStringMap(command)
        map.flatMap {
          case (name, statement) =>
            treesToReplacements(split, name, command)
        }
    }

  private def treesToReplacements(split: SplitExpressionsNoBlankies, name: String, command: Seq[String]) =
    split.settingsTrees.foldLeft(Seq.empty[(Int, String, String)]) {
      case (acc, (st, tree)) =>
        val treeName = extractSettingName(tree)
        if (name == treeName) {
          val replacement = if (acc.isEmpty) {
            command.mkString(END_OF_LINE)
          } else {
            EMPTY_STRING
          }
          (tree.pos.start, st, replacement) +: acc
        } else {
          acc
        }
    }

  private def toTreeStringMap(command: Seq[String]) = {
    val split = SplitExpressionsNoBlankies(FAKE_FILE, command)
    val trees = split.settingsTrees
    val seq = trees.map {
      case (statement, tree) =>
        (extractSettingName(tree), statement)
    }
    seq.toMap
  }

  private def extractSettingName(tree: Tree): String =
    tree.children match {
      case h :: _ =>
        extractSettingName(h)
      case _ =>
        tree.toString()
    }

}
