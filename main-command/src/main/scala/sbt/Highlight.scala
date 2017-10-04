/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.util.regex.Pattern
import scala.Console.{ BOLD, RESET }

import sbt.internal.util.ConsoleAppender

object Highlight {

  def showMatches(pattern: Pattern)(line: String): Option[String] = {
    val matcher = pattern.matcher(line)
    if (ConsoleAppender.formatEnabledInEnv) {
      // ANSI codes like \033[39m (normal text color) don't work on Windows
      val highlighted = matcher.replaceAll(scala.Console.RED + "$0" + RESET)
      if (highlighted == line) None else Some(highlighted)
    } else if (matcher.find)
      Some(line)
    else
      None
  }
  def bold(s: String) =
    if (ConsoleAppender.formatEnabledInEnv) BOLD + s.replace(RESET, RESET + BOLD) + RESET else s
}
