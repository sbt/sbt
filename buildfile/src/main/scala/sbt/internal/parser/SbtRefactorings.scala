/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.core.Contexts.Context

private[sbt] object SbtRefactorings:

  import sbt.internal.parser.SbtParser.{ END_OF_LINE, FAKE_FILE }

  /** A session setting is simply a tuple of a Setting[_] and the strings which define it. */
  type SessionSetting = (Def.Setting[_], Seq[String])
  type SbtConfigFile = (File, Seq[String])
  val emptyString = ""
  val reverseOrderingInt = Ordering[Int].reverse

  /**
   * Refactoring a `.sbt` file so that the new settings are used instead of any existing settings.
   * @param configFile SbtConfigFile with the lines of an sbt file as a List[String] where each string is one line
   * @param commands A List of settings (space separate) that should be inserted into the current file.
   *                 If the settings replaces a value, it will replace the original line in the .sbt file.
   *                 If in the `.sbt` file we have multiply value for one settings -
   *                 the first will be replaced and the other will be removed.
   * @return a SbtConfigFile with new lines which represent the contents of the refactored .sbt file.
   */
  def applySessionSettings(
      configFile: SbtConfigFile,
      commands: Seq[SessionSetting]
  ): SbtConfigFile = {
    val (file, lines) = configFile
    val split = SbtParser(FAKE_FILE, lines)
    given ctx: Context = SbtParser.defaultGlobalForParser.compileCtx
    val recordedCommands = recordCommands(commands, split)
    val sortedRecordedCommands = recordedCommands.sortBy(_._1)(reverseOrderingInt)

    val newContent = replaceFromBottomToTop(lines.mkString(END_OF_LINE), sortedRecordedCommands)
    (file, newContent.linesIterator.toList)
  }

  private def replaceFromBottomToTop(
      modifiedContent: String,
      sortedRecordedCommands: Seq[(Int, String, String)]
  ) = {
    sortedRecordedCommands.foldLeft(modifiedContent) { case (acc, (from, old, replacement)) =>
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

  private def recordCommands(commands: Seq[SessionSetting], split: SbtParser)(using Context) =
    commands.flatMap { case (_, command) =>
      val map = toTreeStringMap(command)
      map.flatMap { case (name, _) => treesToReplacements(split, name, command) }
    }

  private def treesToReplacements(split: SbtParser, name: String, command: Seq[String])(using
      Context
  ) =
    split.settingsTrees.foldLeft(Seq.empty[(Int, String, String)]) { case (acc, (st, tree)) =>
      val treeName = extractSettingName(tree)
      if (name == treeName) {
        val replacement =
          if (acc.isEmpty) command.mkString(END_OF_LINE)
          else emptyString
        (tree.sourcePos.start, st, replacement) +: acc
      } else {
        acc
      }
    }

  private def toTreeStringMap(command: Seq[String]) = {
    val split = SbtParser(FAKE_FILE, command)
    val trees = split.settingsTrees
    val seq = trees.map { case (statement, tree) =>
      (extractSettingName(tree), statement)
    }
    seq.toMap
  }

  // todo: revisit
  private def extractSettingName(tree: untpd.Tree): String =
    tree.toString()
    // tree.children match {
    //   case h :: _ =>
    //     extractSettingName(h)
    //   case _ =>
    //     tree.toString()
    // }

end SbtRefactorings
