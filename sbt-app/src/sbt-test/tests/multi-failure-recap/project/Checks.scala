package sbt
package multifailurerecap

import sbt.internal.testing.TestRecap

/**
 * Lives in package `sbt` so it can reach `TestRecap`, which is `private[sbt]`.
 * Verifies the recap artifact that `Aggregation.runTasks` writes after an
 * aggregated test failure.
 */
object Checks {
  def checkRecap(baseDir: java.io.File): Unit = {
    val file = TestRecap.artifactFile(baseDir)
    assert(file.exists, s"recap artifact not written at ${file.getAbsolutePath}")
    val text = sbt.io.IO.read(file)
    assert(text.startsWith("Test failures recap (2 test tasks failed):"),
      s"recap should start with header, got:\n$text")
    assert(text.contains("a / Test / test"), s"recap missing project a:\n$text")
    assert(text.contains("c / Test / test"), s"recap missing project c:\n$text")
    assert(text.contains("FailingTestA"), s"recap missing FailingTestA:\n$text")
    assert(text.contains("FailingTestC"), s"recap missing FailingTestC:\n$text")
    // b passed and must not appear in the recap.
    assert(!text.contains("b / Test / test"),
      s"passing project b should not appear in recap:\n$text")
    // ASCII-only output for terminal/CI compatibility.
    assert(text.forall(ch => ch < 128),
      s"non-ASCII characters in recap: $text")
  }

  def checkNoRecap(baseDir: java.io.File): Unit = {
    val file = TestRecap.artifactFile(baseDir)
    assert(!file.exists,
      s"recap artifact should have been removed at ${file.getAbsolutePath}")
  }
}
