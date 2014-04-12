package sbt.complete

import org.specs2.mutable.Specification
import org.specs2.specification.Scope
import Completion._

class ParserWithExamplesTest extends Specification {

	"listing a limited number of completions" should {
		"grab only the needed number of elements the iterable source of examples" in new parserWithLazyExamples {
			parserWithExamples.completions(0)
			examples.size shouldEqual maxNumberOfExamples
		}
	}

	"listing only valid completions" should {
		"remove invalid examples" in new parserWithValidExamples {
			val validCompletions = Completions(Set(
				suggestion("blue"),
				suggestion("red")
			))
			parserWithExamples.completions(0) shouldEqual validCompletions
		}
	}

	"listing completions in a derived parser" should {
		"produce only examples that match the derivation" in new parserWithValidExamples {
			val derivedCompletions = Completions(Set(
				suggestion("lue")
			))
			parserWithExamples.derive('b').completions(0) shouldEqual derivedCompletions
		}
	}

	"listing unfiltered completions" should {
		"produce all examples" in new parserWithAllExamples {
			val completions = Completions(examples.map(suggestion(_)).toSet)
			parserWithExamples.completions(0) shouldEqual completions
		}
	}

	"listing completions in a derived parser" should {
		"produce only examples that match the derivation" in new parserWithAllExamples {
			val derivedCompletions = Completions(Set(
				suggestion("lue"),
				suggestion("lock")
			))
			parserWithExamples.derive('b').completions(0) shouldEqual derivedCompletions
		}
	}

	class parserWithLazyExamples extends parser(GrowableSourceOfExamples(), maxNumberOfExamples = 5, removeInvalidExamples = false)

	class parserWithValidExamples extends parser(removeInvalidExamples = true)

	class parserWithAllExamples extends parser(removeInvalidExamples = false)

	case class parser(examples: Iterable[String] = Set("blue", "yellow", "greeen", "block", "red"),
										maxNumberOfExamples: Int = 25,
										removeInvalidExamples: Boolean) extends Scope {

		import DefaultParsers._

		val colorParser = "blue" | "green" | "black" | "red"
		val parserWithExamples = new ParserWithExamples[String](
			colorParser,
			FixedSetExamples(examples),
			maxNumberOfExamples,
			removeInvalidExamples
		)
	}

	case class GrowableSourceOfExamples() extends Iterable[String] {
		var numberOfIteratedElements: Int = 0

		override def iterator: Iterator[String] = {
			new Iterator[String] {
				var currentElement = 0

				override def next(): String = {
					currentElement += 1
					numberOfIteratedElements = Math.max(currentElement, numberOfIteratedElements)
					numberOfIteratedElements.toString
				}

				override def hasNext: Boolean = true
			}
		}

		override def size: Int = numberOfIteratedElements
	}

}
