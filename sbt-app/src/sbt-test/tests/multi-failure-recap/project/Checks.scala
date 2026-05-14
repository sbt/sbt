package sbt
package multifailurerecap

import sbt.internal.testing.TestRecap

/**
 * Lives in package `sbt` so it can access `TestRecap`, which is `private[sbt]`.
 *
 * A scripted statement that fails (`-> test`) closes the inner sbt's IPC
 * server (see `SbtHandler.onNewSbtInstance`'s catch block), which terminates
 * the inner sbt JVM. Scripted then launches a fresh JVM for the next
 * statement, so the State attribute set by Aggregation.runTasks cannot be
 * read by a follow-up `> check`. To verify recap content end-to-end we
 * stay inside a single sbt invocation: the `verifyRecap` command runs
 * `test` via `Command.process`, inspects the resulting state's attribute,
 * and discards the failure so its own state is not failed.
 */
object Checks {

  val verifyRecap: Command = Command.command("verifyRecap") { state =>
    val afterTest = Command.process("test", state)
    val recap = afterTest.get(TestRecap.recapKey).getOrElse {
      sys.error("TestRecap.recapKey not set on state after aggregated test failure")
    }
    val names = recap.map(_.taskName).toSet
    // `test := testQuick.evaluated` (Defaults.scala), so the recorded
    // taskName is `<proj> / Test / testQuick` rather than `... / test`.
    assert(recap.size == 2, s"expected 2 failures, got ${recap.size}: $names")
    assert(names.exists(_.startsWith("a / ")), s"recap missing project a: $names")
    assert(names.exists(_.startsWith("c / ")), s"recap missing project c: $names")
    assert(!names.exists(_.startsWith("b / ")), s"recap should not list project b: $names")
    recap.foreach { f =>
      assert(f.testOutput.isDefined, s"${f.taskName} has no Tests.Output payload")
      val failedSuites = f.testOutput.get.events.values.count: s =>
        s.result == sbt.protocol.testing.TestResult.Failed
      assert(failedSuites >= 1, s"${f.taskName} has no failed suite: ${f.testOutput.get.events}")
    }
    // Sanity check rendering.
    val lines = TestRecap.render(recap)
    assert(lines.head.startsWith("Test failures recap (2 test tasks failed):"),
      s"unexpected header: ${lines.head}")
    lines.foreach(l => assert(l.forall(_ < 128), s"non-ASCII in recap line: $l"))
    // Return the *original* state so the verifyRecap command itself is not
    // marked as failed; the failure was structural to `test`, which we
    // have already inspected.
    state
  }

  val verifyNoRecap: Command = Command.command("verifyNoRecap") { state =>
    val afterTest = Command.process("test", state)
    afterTest.get(TestRecap.recapKey) match {
      case None    => state
      case Some(r) => sys.error(s"unexpected recap after passing test run: ${r.map(_.taskName)}")
    }
  }
}
