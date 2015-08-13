package sbt.inc

import xsbt.TestAnalyzingCompiler

import org.specs2.Specification

class IncrementalTest extends Specification {
  import IncrementalCompilerTest._

  def is = s2"""

  This is a specification for the incremental compiler.

  The incremental compiler should
    execute the first scenario                                                   $firstScenario
    execute the second scenario                                                  $secondScenario
    execute the third scenario                                                   $thirdScenario
    execute the fourth scenario                                                  $fourthScenario
    execute the fifth scenario                                                   $fifthScenario
    execute the sixth scenario                                                   $sixthScenario

  """

  def compiler = new TestAnalyzingCompiler(sbt.inc.IncOptions.Default)

  def firstScenario =
    compiler execute Scenario(
      FailedCompile(
        "A.scala" -> "hello"))

  def secondScenario =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "A.scala" -> "object A extends B",
        "B.scala" -> "class B"),

      FailedCompile(
        "B.scala" -> "final class B"),

      FailedCompile(
        "B.scala" -> delete),

      IncrementalStep(
        "A.scala" -> "object A"))

  def thirdScenario =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "A.scala" -> "class A extends B",
        "B.scala" -> "class B"),

      IncrementalStep(
        "B.scala" -> "class B { def foo = 1 }") invalidates ("A.scala"),

      FullCompilation(
        expectedSteps = 1,
        "A.scala" -> "class A extends B { override def foo = 2 }"),

      FailedCompile(
        "B.scala" -> delete),

      FullCompilation(
        expectedSteps = 1,
        "A.scala" -> "class A { def foo = 2 }"))

  def fourthScenario =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "C.scala" -> "class C extends A",
        "A.scala" -> "class A extends B",
        "B.scala" -> "class B"),

      IncrementalStep(
        "B.scala" -> "class B { def foo = 1 }") invalidates ("A.scala", "C.scala"),

      FullCompilation(
        expectedSteps = 2,
        "A.scala" -> "class A extends B { override def foo = 2 }"),

      FailedCompile(
        "B.scala" -> delete))

  def fifthScenario =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "C.scala" -> "class C extends A",
        "A.scala" -> "class A extends B",
        "B.scala" -> "class B"),

      IncrementalStep(
        "B.scala" -> """class B {
                       |  def foo = 1
                       |}""".stripMargin) invalidates ("A.scala", "C.scala"),

      FullCompilation(
        expectedSteps = 2,
        "A.scala" -> """class A extends B {
                       |  override def foo = 2
                       |}""".stripMargin),

      FailedCompile(
        "B.scala" -> delete))

  def sixthScenario =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "C.scala" -> "class C extends A",
        "A.scala" -> "class A extends B",
        "B.scala" -> "class B"),

      IncrementalStep(
        "B.scala" -> """class B {
                       |  def foo = 1
                       |}""".stripMargin) invalidates ("A.scala", "C.scala"),

      IncrementalStep() invalidates (),

      IncrementalStep(
        "A.scala" -> """class A extends B {
                       |  override def foo = 2
                       |}""".stripMargin) invalidates ("C.scala"),

      FailedCompile(
        "B.scala" -> delete))

}
