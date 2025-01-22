package sbt.internal

object ForkWorkerTest extends verify.BasicTestSuite:
  test("test") {
    assert(
      ForkWorker.runWithCurrentClasspath(
        mainClass = classOf[ConsoleMain].getCanonicalName,
        args = List(),
      ) == 0
    )
  }
end ForkWorkerTest
