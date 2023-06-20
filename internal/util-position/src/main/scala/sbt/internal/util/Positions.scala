/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

sealed trait SourcePosition

sealed trait FilePosition extends SourcePosition {
  def path: String
  def startLine: Int
}

case object NoPosition extends SourcePosition

final case class LinePosition(path: String, startLine: Int) extends FilePosition

final case class LineRange(start: Int, end: Int) {
  def shift(n: Int) = new LineRange(start + n, end + n)
}

final case class RangePosition(path: String, range: LineRange) extends FilePosition {
  def startLine = range.start
}

object SourcePosition extends SourcePositionImpl
