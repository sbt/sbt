/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import org.scalacheck.*, Gen.*, Prop.*

object DefaultParsersSpec extends Properties("DefaultParsers") {
  import DefaultParsers.{ ID, isIDChar, matches, validID }

  property("∀ s ∈ String: validID(s) == matches(ID, s)") =
    forAll((s: String) => validID(s) == matches(ID, s))

  property("∀ s ∈ genID: matches(ID, s)") = forAll(genID)(s => matches(ID, s))
  property("∀ s ∈ genID: validID(s)") = forAll(genID)(s => validID(s))

  private val chars: Seq[Char] = Char.MinValue to Char.MaxValue
  private val genID: Gen[String] =
    for {
      c <- oneOf(chars filter (_.isLetter))
      cs <- listOf(oneOf(chars filter isIDChar))
    } yield (c :: cs).mkString
}
