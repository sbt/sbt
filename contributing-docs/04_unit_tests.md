sbt Unit tests
==============

Various functional and unit tests are defined throughout the
project.

- To run all of them, run `test` in the sbt shell.
- You can run a single test suite with `testOnly **.FooTest`.
- You can run an incremental test as `testQuick`.
- You can run an incremental test scoped to a subproject as `commandProj/testQuick`

Property-based testing with HedgeHog
------------------------------------

sbt contains various testing frameworks, but our preferred unit testing framework is HedgeHog.

See [main-actions/src/test/scala/sbt/internal/WorkerExchangeTest.scala](../main-actions/src/test/scala/sbt/internal/WorkerExchangeTest.scala) for an example.

```scala
package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.{ ShrinkLimit, SuccessCount }
import hedgehog.core.Result
import scala.sys.process.Process

object WorkerExchangeTest extends Properties:
  given Gen[WorkerConnection] =
    Gen.choice1(Gen.constant(WorkerConnection.Stdio), Gen.constant(WorkerConnection.Tcp))

  def gen[A1: Gen]: Gen[A1] = summon[Gen[A1]]

  override lazy val tests: List[Test] = List(
    propertyN("non-jsonrpc should return exit code 1", propBadInput, 10),
  )

  def propertyN(name: String, result: => Property, n: Int): Test =
    Test(name, result)
      .config(_.copy(testLimit = SuccessCount(n), shrinkLimit = ShrinkLimit(n * 10)))

  def propBadInput: Property =
    for
      ct <- gen[WorkerConnection].forAll
      w = WorkerExchange.startWorker(ForkOptions(), Nil, ct)
    yield
      w.println("{}")
      val exitCode = w.blockForExitCode()
      Result.assert(exitCode == 1)
end WorkerExchangeTest
```

Unit testing with HedgeHog
--------------------------

HedgeHog can be used for simple unit testing as well using `example(...)`:

```scala
package sbt.internal.util

import hedgehog.*
import hedgehog.runner.*

object SourcePositionTest extends Properties:
  override def tests: List[Test] = List(
    example(
      "SourcePosition() should return a SourcePosition", {
        val filename = "SourcePositionTest.scala"
        val lineNumber = 19
        SourcePosition.fromEnclosing() match
          case pos @ LinePosition(path, startLine) =>
            Result.assert(path == filename && startLine == lineNumber)
              .log(pos.toString())
      }
    )
  )
end SourcePositionTest
```

Unit testing with Verify
------------------------

Verify can also be used for unit tests:

```scala
package sbt.util

import sjsonnew.BasicJsonProtocol
import sjsonnew.support.murmurhash.Hasher
import verify.BasicTestSuite

object HasherTest extends BasicTestSuite:
  test("The IntJsonFormat should convert an Int to an int hash") {
    import BasicJsonProtocol.given
    val actual = Hasher.hashUnsafe[Int](1)
    assert(actual == 1527037976)
  }
end HasherTest
```
