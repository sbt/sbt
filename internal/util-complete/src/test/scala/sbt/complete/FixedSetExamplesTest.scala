package sbt.util.internal
package complete

import org.specs2.mutable.Specification
import org.specs2.specification.Scope

class FixedSetExamplesTest extends Specification {

  "adding a prefix" should {
    "produce a smaller set of examples with the prefix removed" in new examples {
      fixedSetExamples.withAddedPrefix("f")() must containTheSameElementsAs(List("oo", "ool", "u"))
      fixedSetExamples.withAddedPrefix("fo")() must containTheSameElementsAs(List("o", "ol"))
      fixedSetExamples.withAddedPrefix("b")() must containTheSameElementsAs(List("ar"))
    }
  }

  "without a prefix" should {
    "produce the original set" in new examples {
      fixedSetExamples() mustEqual exampleSet
    }
  }

  trait examples extends Scope {
    val exampleSet = List("foo", "bar", "fool", "fu")
    val fixedSetExamples = FixedSetExamples(exampleSet)
  }
}
