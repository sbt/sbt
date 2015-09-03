package sbt.inc

import xsbt.TestAnalyzingCompiler

import org.scalatest.FlatSpec

class IncrementalTest extends FlatSpec {
  import IncrementalCompilerTest._

  behavior of "The incremental compiler"

  it should "execute the first scenario" in firstScenario
  it should "execute the second scenario" in secondScenario
  it should "execute the third scenario" in thirdScenario
  it should "execute the fourth scenario" in fourthScenario
  it should "execute the fifth scenario" in fifthScenario
  it should "execute the sixth scenario" in sixthScenario
  it should "consider private members of traits in invalidation" in traitPrivateMembers

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

  def traitPrivateMembers =
    compiler execute Scenario(
      FullCompilation(
        expectedSteps = 1,
        "Bar.scala" -> """trait Bar {
                         |  private val x = new A
                         |}""".stripMargin,
        "Classes.scala" -> """class A
                             |class B""".stripMargin,
        "Foo.scala" -> "class Foo extends Bar"),

      IncrementalStep(
        "Bar.scala" -> """trait Bar {
                         |  private val x = new B
                         |}""".stripMargin) invalidates ("Foo.scala")).pending
}
