package sbt.internal

object ForkWorkerTest extends verify.BasicTestSuite:
  test("test") {
    assert(
      ForkWorker.runWithCurrentClasspath(
        mainClass = classOf[ConsoleMain].getCanonicalName,
        args = List("foo"),
      ) == 0
    )
  }
end ForkWorkerTest
