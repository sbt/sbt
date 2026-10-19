/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import hedgehog.*
import hedgehog.runner.*

object KeyTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "AttributeKey with the same label and type are equal",
      same(AttributeKey[Int]("test"), AttributeKey[Int]("test"))
    ),
    example(
      "AttributeKey equality ignores the description",
      same(AttributeKey[Int]("test"), AttributeKey[Int]("test", "description"))
    ),
    example(
      "AttributeKey equality ignores differing descriptions",
      same(AttributeKey[Int]("test", "a"), AttributeKey[Int]("test", "b"))
    ),
    example(
      "AttributeKey with different labels are not equal",
      different(AttributeKey[Int]("test"), AttributeKey[Int]("tests"))
    ),
    example(
      "AttributeKey with different types are not equal",
      different(AttributeKey[Int]("test"), AttributeKey[Double]("test"))
    ),
    example(
      "AttributeKey distinguishes a boxed Integer from Int",
      different(AttributeKey[java.lang.Integer]("test"), AttributeKey[Int]("test"))
    ),
    example(
      "AttributeKey with the same type arguments are equal",
      same(AttributeKey[Map[Int, String]]("test"), AttributeKey[Map[Int, String]]("test"))
    ),
    example(
      "AttributeKey compares erased types, so type arguments do not distinguish keys",
      same(AttributeKey[Map[Int, String]]("test"), AttributeKey[Map[Int, ?]]("test"))
    ),
  )

  def same(a: AttributeKey[?], b: AttributeKey[?]): Result =
    Result
      .all(List(a ==== b, a.hashCode ==== b.hashCode))
      .log(s"a: ${a.label} ${a.tag}, b: ${b.label} ${b.tag}")

  def different(a: AttributeKey[?], b: AttributeKey[?]): Result =
    Result.assert(a != b).log(s"a: ${a.label} ${a.tag}, b: ${b.label} ${b.tag}")
end KeyTest
