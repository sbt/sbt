/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.nio.file.{ Path, Paths }
import java.util.Locale

import scala.collection.concurrent.TrieMap
import scala.reflect.Selectable.reflectiveSelectable
import scala.util.Properties

object Util:
  def makeList[T](size: Int, value: T): List[T] = List.fill(size)(value)

  def separate[T, A, B](ps: Seq[T])(f: T => Either[A, B]): (Seq[A], Seq[B]) = {
    val (a, b) = ps.foldLeft((Nil: Seq[A], Nil: Seq[B]))((xs, y) => prependEither(xs, f(y)))
    (a.reverse, b.reverse)
  }

  def prependEither[A, B](acc: (Seq[A], Seq[B]), next: Either[A, B]): (Seq[A], Seq[B]) =
    next match {
      case Left(l)  => (l +: acc._1, acc._2)
      case Right(r) => (acc._1, r +: acc._2)
    }

  def pairID[A, B] = (a: A, b: B) => (a, b)

  private lazy val Hyphen = """-(\p{javaLowerCase})""".r

  def hasHyphen(s: String): Boolean = s.indexOf('-') >= 0

  def hyphenToCamel(s: String): String =
    if (hasHyphen(s)) Hyphen.replaceAllIn(s, _.group(1).toUpperCase(Locale.ENGLISH)) else s

  private lazy val Camel = """(\p{javaLowerCase})(\p{javaUpperCase})""".r

  def camelToHyphen(s: String): String =
    Camel.replaceAllIn(s, m => m.group(1) + "-" + m.group(2).toLowerCase(Locale.ENGLISH))

  def quoteIfKeyword(s: String): String = if (ScalaKeywords.values(s)) s"`${s}`" else s

  def quoteIfNotScalaId(s: String): String =
    if isValidScalaId(s) && !ScalaKeywords.values(s) then s
    else s"`$s`"

  private def isValidScalaId(s: String): Boolean =
    s.nonEmpty && (s.charAt(0).isLetter || s.charAt(0) == '_') &&
      s.forall(c => c.isLetterOrDigit || c == '_')

  def ignoreResult[A](f: => A): Unit = {
    val _ = f
    ()
  }

  lazy val isMac: Boolean =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("mac")

  lazy val isWindows: Boolean =
    System.getProperty("os.name").toLowerCase(Locale.ENGLISH).contains("windows")

  lazy val isCygwin: Boolean = {
    val os = sys.env.get("OSTYPE")
    os match {
      case Some(x) => x.toLowerCase(Locale.ENGLISH).contains("cygwin")
      case _       => false
    }
  }

  lazy val isNonCygwinWindows: Boolean = isWindows && !isCygwin
  lazy val isCygwinWindows: Boolean = isWindows && isCygwin

  lazy val isEmacs: Boolean = sys.env.contains("INSIDE_EMACS")

  def nil[A]: List[A] = List.empty[A]
  def nilSeq[A]: Seq[A] = Seq.empty[A]
  def none[A]: Option[A] = (None: Option[A])

  extension [A](value: A) {
    def some: Option[A] = (Some(value): Option[A])
  }

  private[sbt] def withCaching[A1, A2](f: A1 => A2): A1 => A2 = {
    val cache = TrieMap.empty[A1, A2]
    x => cache.getOrElseUpdate(x, f(x))
  }

  lazy val javaHome: Path =
    sys.env.get("JAVA_HOME") match
      case Some(home) => Paths.get(home)
      case None =>
        if sys.props("java.home").endsWith("jre") then Paths.get(sys.props("java.home")).getParent()
        else Paths.get(sys.props("java.home"))

  /**
   * Given a list of event handlers expressed partial functions, combine them
   * together using orElse from the left.
   */
  def reduceIntents[A1, A2](intents: PartialFunction[A1, A2]*): PartialFunction[A1, A2] =
    intents.toList.reduceLeft(_ orElse _)

  lazy val isJava19Plus: Boolean = Properties.isJavaAtLeast("19")

  private type GetId = {
    def getId: Long
  }
  private type ThreadId = {
    def threadId: Long
  }

  /**
   * Returns current thread id.
   * Thread.threadId was added in JDK 19, and deprecated Thread#getId
   * https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#threadId()
   */
  def threadId: Long =
    if !isJava19Plus then
      (Thread.currentThread(): AnyRef) match
        case g: GetId @unchecked => g.getId
    else
      (Thread.currentThread(): AnyRef) match
        case g: ThreadId @unchecked => g.threadId
end Util
