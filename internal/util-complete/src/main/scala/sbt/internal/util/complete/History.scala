/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

import History.number
import java.io.File

final class History private (
    val lines: IndexedSeq[String],
    val path: Option[File],
    error: String => Unit
) {
  private def reversed = lines.reverse

  def all: Seq[String] = lines
  def size = lines.length
  def !! : Option[String] = !-(1)

  def apply(i: Int): Option[String] =
    if (0 <= i && i < size) Some(lines(i)) else { sys.error("Invalid history index: " + i) }

  def !(i: Int): Option[String] = apply(i)

  def !(s: String): Option[String] =
    number(s) match {
      case Some(n) => if (n < 0) !-(-n) else apply(n)
      case None    => nonEmpty(s) { reversed.find(_.startsWith(s)) }
    }

  def !-(n: Int): Option[String] = apply(size - n - 1)

  def !?(s: String): Option[String] = nonEmpty(s) { reversed.drop(1).find(_.contains(s)) }

  private def nonEmpty[T](s: String)(act: => Option[T]): Option[T] =
    if (s.isEmpty)
      sys.error("No action specified to history command")
    else
      act

  def list(historySize: Int, show: Int): Seq[String] =
    lines.toList
      .drop(scala.math.max(0, lines.size - historySize))
      .zipWithIndex
      .map { case (line, number) => "   " + number + "  " + line }
      .takeRight(show max 1)
}

object History {
  def apply(lines: Seq[String], path: Option[File], error: String => Unit): History =
    new History(lines.toIndexedSeq, path, sys.error)

  def number(s: String): Option[Int] =
    try { Some(s.toInt) } catch { case _: NumberFormatException => None }
}
