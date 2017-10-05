/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package complete

/**
 * Represents a set of completions.
 * It exists instead of implicitly defined operations on top of Set[Completion]
 *  for laziness.
 */
sealed trait Completions {
  def get: Set[Completion]

  final def x(o: Completions): Completions = flatMap(_ x o)
  final def ++(o: Completions): Completions = Completions(get ++ o.get)
  final def +:(o: Completion): Completions = Completions(get + o)
  final def filter(f: Completion => Boolean): Completions = Completions(get filter f)
  final def filterS(f: String => Boolean): Completions = filter(c => f(c.append))

  override def toString = get.mkString("Completions(", ",", ")")

  final def flatMap(f: Completion => Completions): Completions =
    Completions(get.flatMap(c => f(c).get))

  final def map(f: Completion => Completion): Completions = Completions(get map f)

  override final def hashCode = get.hashCode
  override final def equals(o: Any) = o match {
    case c: Completions => get == c.get; case _ => false
  }
}

object Completions {

  /** Returns a lazy Completions instance using the provided Completion Set. */
  def apply(cs: => Set[Completion]): Completions = new Completions {
    lazy val get = cs
  }

  /** Returns a strict Completions instance using the provided Completion Set. */
  def strict(cs: Set[Completion]): Completions = apply(cs)

  /**
   * No suggested completions, not even the empty Completion.
   * This typically represents invalid input.
   */
  val nil: Completions = strict(Set.empty)

  /**
   * Only includes an empty Suggestion.
   * This typically represents valid input that either has no completions or accepts no further input.
   */
  val empty: Completions = strict(Set.empty + Completion.empty)

  /** Returns a strict Completions instance containing only the provided Completion.*/
  def single(c: Completion): Completions = strict(Set.empty + c)

}

/**
 * Represents a completion.
 * The abstract members `display` and `append` are best explained with an example.
 *
 * Assuming space-delimited tokens, processing this:
 *   am is are w<TAB>
 * could produce these Completions:
 *   Completion { display = "was"; append = "as" }
 *   Completion { display = "were"; append = "ere" }
 * to suggest the tokens "was" and "were".
 *
 * In this way, two pieces of information are preserved:
 *  1) what needs to be appended to the current input if a completion is selected
 *  2) the full token being completed, which is useful for presenting a user with choices to select
 */
sealed trait Completion {

  /** The proposed suffix to append to the existing input to complete the last token in the input.*/
  def append: String

  /** The string to present to the user to represent the full token being suggested.*/
  def display: String

  /** True if this Completion is suggesting the empty string.*/
  def isEmpty: Boolean

  /** Appends the completions in `o` with the completions in this Completion.*/
  def ++(o: Completion): Completion = Completion.concat(this, o)

  final def x(o: Completions): Completions =
    if (Completion evaluatesRight this) o.map(this ++ _) else Completions.strict(Set.empty + this)

  override final lazy val hashCode = Completion.hashCode(this)
  override final def equals(o: Any) = o match {
    case c: Completion => Completion.equal(this, c); case _ => false
  }
}

final class DisplayOnly(val display: String) extends Completion {
  def isEmpty = display.isEmpty
  def append = ""
  override def toString = "{" + display + "}"
}

final class Token(val display: String, val append: String) extends Completion {
  def isEmpty = display.isEmpty && append.isEmpty
  override final def toString = "[" + display + "]++" + append
}

final class Suggestion(val append: String) extends Completion {
  def isEmpty = append.isEmpty
  def display = append
  override def toString = append
}

object Completion {
  def concat(a: Completion, b: Completion): Completion =
    (a, b) match {
      case (as: Suggestion, bs: Suggestion)    => suggestion(as.append + bs.append)
      case (at: Token, _) if at.append.isEmpty => b
      case _ if a.isEmpty                      => b
      case _                                   => a
    }

  def evaluatesRight(a: Completion): Boolean =
    a match {
      case _: Suggestion                  => true
      case at: Token if at.append.isEmpty => true
      case _                              => a.isEmpty
    }

  def equal(a: Completion, b: Completion): Boolean =
    (a, b) match {
      case (as: Suggestion, bs: Suggestion)   => as.append == bs.append
      case (ad: DisplayOnly, bd: DisplayOnly) => ad.display == bd.display
      case (at: Token, bt: Token)             => at.display == bt.display && at.append == bt.append
      case _                                  => false
    }

  def hashCode(a: Completion): Int =
    a match {
      case as: Suggestion  => (0, as.append).hashCode
      case ad: DisplayOnly => (1, ad.display).hashCode
      case at: Token       => (2, at.display, at.append).hashCode
    }

  val empty: Completion = suggestion("")
  def single(c: Char): Completion = suggestion(c.toString)

  // TODO: make strict in 0.13.0 to match DisplayOnly
  def displayOnly(value: => String): Completion = new DisplayOnly(value)

  // TODO: make strict in 0.13.0 to match Token
  def token(prepend: => String, append: => String): Completion =
    new Token(prepend + append, append)

  /** @since 0.12.1 */
  def tokenDisplay(append: String, display: String): Completion = new Token(display, append)

  // TODO: make strict in 0.13.0 to match Suggestion
  def suggestion(value: => String): Completion = new Suggestion(value)
}
