/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

object ProcessError {
  def apply(command: String, msgs: Seq[String], index: Int): String = {
    val (line, modIndex) = extractLine(command, index)
    val point = pointerSpace(command, modIndex)
    msgs.mkString("\n") + "\n" + line + "\n" + point + "^"
  }

  def extractLine(s: String, i: Int): (String, Int) = {
    val notNewline = (c: Char) => c != '\n' && c != '\r'
    val left = takeRightWhile(s.substring(0, i))(notNewline)
    val right = s substring i takeWhile notNewline
    (left + right, left.length)
  }

  def takeRightWhile(s: String)(pred: Char => Boolean): String = {
    def loop(i: Int): String =
      if (i < 0)
        s
      else if (pred(s(i)))
        loop(i - 1)
      else
        s.substring(i + 1)
    loop(s.length - 1)
  }

  def pointerSpace(s: String, i: Int): String =
    (s take i) map { case '\t' => '\t'; case _ => ' ' } mkString ""
}
