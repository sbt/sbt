/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

trait ShowLines[A] {
  def showLines(a: A): Seq[String]
}
object ShowLines {
  def apply[A](f: A => Seq[String]): ShowLines[A] =
    new ShowLines[A] {
      def showLines(a: A): Seq[String] = f(a)
    }

  implicit class ShowLinesOp[A: ShowLines](a: A) {
    def lines: Seq[String] = implicitly[ShowLines[A]].showLines(a)
  }
}
