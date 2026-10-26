/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

import hedgehog.*
import hedgehog.runner.*

object FixedSetExamplesTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "adding a prefix should produce a smaller set of examples with the prefix removed",
      testAddedPrefix
    ),
    example(
      "without a prefix should produce the original set",
      fixedSetExamples().toList ==== exampleSet
    ),
  )

  val exampleSet = List("foo", "bar", "fool", "fu")
  def fixedSetExamples = FixedSetExamples(exampleSet)

  def testAddedPrefix: Result =
    Result.all(
      List(
        fixedSetExamples.withAddedPrefix("f")().toList.sorted ==== List("oo", "ool", "u"),
        fixedSetExamples.withAddedPrefix("fo")().toList.sorted ==== List("o", "ol"),
        fixedSetExamples.withAddedPrefix("b")().toList ==== List("ar"),
      )
    )
end FixedSetExamplesTest
