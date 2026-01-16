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
  def sourceCode: Option[String]
}

case object NoPosition extends SourcePosition

final case class LinePosition(path: String, startLine: Int) extends FilePosition {
  private var _sourceCode: Option[String] = None
  def sourceCode: Option[String] = _sourceCode
  def withSourceCode(code: String): LinePosition =
    val o = copy()
    o._sourceCode = Some(code)
    o
  def withSourceCode(c: Option[String]): LinePosition =
    c match
      case Some(code) => this.withSourceCode(code)
      case None       => this
}

final case class LineRange(start: Int, end: Int) {
  def shift(n: Int) = new LineRange(start + n, end + n)
}

final case class RangePosition(path: String, range: LineRange) extends FilePosition {
  private var _sourceCode: Option[String] = None
  def startLine = range.start
  def sourceCode: Option[String] = _sourceCode
  def withSourceCode(code: String): RangePosition =
    val o = copy()
    o._sourceCode = Some(code)
    o
  def withSourceCode(c: Option[String]): RangePosition =
    c match
      case Some(code) => this.withSourceCode(code)
      case None       => this
}

object SourcePosition extends SourcePositionImpl
