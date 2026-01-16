/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.regex.Pattern
import scala.Console.{ BOLD, RESET }

import sbt.internal.util.{ Terminal as UTerminal }

object Highlight {

  def showMatches(pattern: Pattern)(line: String): Option[String] = {
    val matcher = pattern.matcher(line)
    if (UTerminal.isColorEnabled) {
      // ANSI codes like \033[39m (normal text color) don't work on Windows
      val highlighted = matcher.replaceAll(scala.Console.RED + "$0" + RESET)
      if (highlighted == line) None else Some(highlighted)
    } else if (matcher.find)
      Some(line)
    else
      None
  }
  def bold(s: String) =
    if (UTerminal.isColorEnabled) BOLD + s.replace(RESET, RESET + BOLD) + RESET else s
}
