/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util

import java.util.Locale

object Util {
  def makeList[T](size: Int, value: T): List[T] = List.fill(size)(value)

  def separateE[A, B](ps: Seq[Either[A, B]]): (Seq[A], Seq[B]) =
    separate(ps)(Types.idFun)

  def separate[T, A, B](ps: Seq[T])(f: T => Either[A, B]): (Seq[A], Seq[B]) = {
    val (a, b) = ((Nil: Seq[A], Nil: Seq[B]) /: ps)((xs, y) => prependEither(xs, f(y)))
    (a.reverse, b.reverse)
  }

  def prependEither[A, B](acc: (Seq[A], Seq[B]), next: Either[A, B]): (Seq[A], Seq[B]) =
    next match {
      case Left(l)  => (l +: acc._1, acc._2)
      case Right(r) => (acc._1, r +: acc._2)
    }

  def pairID[A, B] = (a: A, b: B) => (a, b)

  private[this] lazy val Hyphen = """-(\p{javaLowerCase})""".r

  def hasHyphen(s: String): Boolean = s.indexOf('-') >= 0

  def hyphenToCamel(s: String): String =
    if (hasHyphen(s)) Hyphen.replaceAllIn(s, _.group(1).toUpperCase(Locale.ENGLISH)) else s

  private[this] lazy val Camel = """(\p{javaLowerCase})(\p{javaUpperCase})""".r

  def camelToHyphen(s: String): String =
    Camel.replaceAllIn(s, m => m.group(1) + "-" + m.group(2).toLowerCase(Locale.ENGLISH))

  def quoteIfKeyword(s: String): String = if (ScalaKeywords.values(s)) '`' + s + '`' else s

  lazy val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows")

  lazy val isCygwin: Boolean = {
    val os = Option(System.getenv("OSTYPE"))
    os match {
      case Some(x) => x.toLowerCase(Locale.ENGLISH).contains("cygwin")
      case _       => false
    }
  }

  lazy val isNonCygwinWindows: Boolean = isWindows && !isCygwin
  lazy val isCygwinWindows: Boolean = isWindows && isCygwin
}
