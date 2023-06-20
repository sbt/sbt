/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util
package complete

class FixedSetExamplesTest extends UnitSpec {

  "adding a prefix" should "produce a smaller set of examples with the prefix removed" in {
    val _ = new Examples {
      fixedSetExamples.withAddedPrefix("f")() should contain theSameElementsAs
        (List("oo", "ool", "u"))
      fixedSetExamples.withAddedPrefix("fo")() should contain theSameElementsAs (List("o", "ol"))
      fixedSetExamples.withAddedPrefix("b")() should contain theSameElementsAs (List("ar"))
    }
  }

  "without a prefix" should "produce the original set" in {
    val _ = new Examples {
      fixedSetExamples() shouldBe exampleSet
    }
  }

  trait Examples {
    val exampleSet = List("foo", "bar", "fool", "fu")
    val fixedSetExamples = FixedSetExamples(exampleSet)
  }
}
