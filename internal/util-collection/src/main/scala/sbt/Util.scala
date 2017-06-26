/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

import java.util.Locale

object Util {
  def makeList[T](size: Int, value: T): List[T] = List.fill(size)(value)

  def separateE[A, B](ps: Seq[Either[A, B]]): (Seq[A], Seq[B]) =
    separate(ps)(Types.idFun)

  def separate[T, A, B](ps: Seq[T])(f: T => Either[A, B]): (Seq[A], Seq[B]) =
    {
      val (a, b) = ((Nil: Seq[A], Nil: Seq[B]) /: ps)((xs, y) => prependEither(xs, f(y)))
      (a.reverse, b.reverse)
    }

  def prependEither[A, B](acc: (Seq[A], Seq[B]), next: Either[A, B]): (Seq[A], Seq[B]) =
    next match {
      case Left(l)  => (l +: acc._1, acc._2)
      case Right(r) => (acc._1, r +: acc._2)
    }

  def pairID[A, B] = (a: A, b: B) => (a, b)

  private[this] lazy val Hypen = """-(\p{javaLowerCase})""".r
  def hasHyphen(s: String): Boolean = s.indexOf('-') >= 0
  @deprecated("Use the properly spelled version: hyphenToCamel", "0.13.0")
  def hypenToCamel(s: String): String = hyphenToCamel(s)
  def hyphenToCamel(s: String): String =
    if (hasHyphen(s))
      Hypen.replaceAllIn(s, _.group(1).toUpperCase(Locale.ENGLISH))
    else
      s

  private[this] lazy val Camel = """(\p{javaLowerCase})(\p{javaUpperCase})""".r
  def camelToHypen(s: String): String =
    Camel.replaceAllIn(s, m => m.group(1) + "-" + m.group(2).toLowerCase(Locale.ENGLISH))

  def quoteIfKeyword(s: String): String = if (ScalaKeywords.values(s)) '`' + s + '`' else s
}
