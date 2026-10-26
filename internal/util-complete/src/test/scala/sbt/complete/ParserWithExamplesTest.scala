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
import Completion.*

object ParserWithExamplesTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "listing a limited number of completions should grab only the needed number of elements from the iterable source of examples",
      testLazyExamples
    ),
    example(
      "listing only valid completions should use the delegate parser to remove invalid examples",
      validExamples.parserWithExamples.completions(0) ====
        Completions(Set(suggestion("blue"), suggestion("red")))
    ),
    example(
      "listing valid completions in a derived parser should produce only valid examples that start with the character of the derivation",
      validExamples.parserWithExamples.derive('b').completions(0) ====
        Completions(Set(suggestion("lue")))
    ),
    example(
      "listing valid and invalid completions should produce the entire source of examples",
      allExamples.parserWithExamples.completions(0) ====
        Completions(allExamples.examples.map(suggestion(_)).toSet)
    ),
    example(
      "listing valid and invalid completions in a derived parser should produce only examples that start with the character of the derivation",
      allExamples.parserWithExamples.derive('b').completions(0) ====
        Completions(Set(suggestion("lue"), suggestion("lock")))
    ),
  )

  def testLazyExamples: Result =
    val p = ParserExample(
      GrowableSourceOfExamples(),
      maxNumberOfExamples = 5,
      removeInvalidExamples = false
    )
    p.parserWithExamples.completions(0)
    p.examples.size ==== p.maxNumberOfExamples

  def validExamples: ParserExample = ParserExample(removeInvalidExamples = true)

  def allExamples: ParserExample = ParserExample(removeInvalidExamples = false)

  case class ParserExample(
      examples: Iterable[String] = Set("blue", "yellow", "green_", "block", "red"),
      maxNumberOfExamples: Int = 25,
      removeInvalidExamples: Boolean
  ):

    import DefaultParsers.*

    val colorParser = "blue" | "green" | "black" | "red"
    val parserWithExamples: Parser[String] = new ParserWithExamples[String](
      colorParser,
      FixedSetExamples(examples),
      maxNumberOfExamples,
      removeInvalidExamples
    )

  case class GrowableSourceOfExamples() extends Iterable[String]:
    private var numberOfIteratedElements: Int = 0

    override def iterator: Iterator[String] =
      new Iterator[String]:
        var currentElement = 0

        override def next(): String =
          currentElement += 1
          numberOfIteratedElements = Math.max(currentElement, numberOfIteratedElements)
          numberOfIteratedElements.toString

        override def hasNext: Boolean = true

    override def size: Int = numberOfIteratedElements
end ParserWithExamplesTest
