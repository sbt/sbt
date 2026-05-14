package sbt
package multifailurerecap

import sbt.internal.testing.TestRecap

/**
 * Lives in package `sbt` so it can access `TestRecap`, which is `private[sbt]`.
 *
 * A scripted statement that fails (`-> test`) closes the inner sbt's IPC
 * server (see `SbtHandler.onNewSbtInstance`'s catch block), which terminates
 * the inner sbt JVM. Scripted then launches a fresh JVM for the next
 * statement, so a State attribute set inside a failing statement cannot be
 * read by a follow-up `> check`. We avoid that by running `test` from
 * inside a Command (here) via `Command.process`, all within one JVM.
 *
 * `recapKey` is monotonic-latest-failure: never proactively cleared.
 * That means across CI scripted shards (which share an inner sbt across
 * tests with `reload;initialize` between them) a prior test that left a
 * recap entry is visible to this test. Both commands therefore strip
 * `recapKey` from the incoming state before doing their own assertions
 * and again on the way out, so they are hermetic w.r.t. anything other
 * scripted tests in the same shard may have left behind.
 */
object Checks {

  val verifyRecap: Command = Command.command("verifyRecap") { state =>
    val cleared = state.remove(TestRecap.recapKey)
    val afterTest = Command.process("test", cleared)
    val recap = afterTest.get(TestRecap.recapKey).getOrElse {
      sys.error("TestRecap.recapKey not set on state after aggregated test failure")
    }
    val names = recap.map(_.taskName).toSet
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
    val lines = TestRecap.render(recap)
    assert(
      lines.head.startsWith("Test failures recap (2 test tasks failed):"),
      s"unexpected header: ${lines.head}"
    )
    // Return the original state with recapKey stripped so the next
    // scripted statement starts hermetic.
    state.remove(TestRecap.recapKey)
  }

  val verifyNoRecap: Command = Command.command("verifyNoRecap") { state =>
    val cleared = state.remove(TestRecap.recapKey)
    val afterTest = Command.process("test", cleared)
    afterTest.get(TestRecap.recapKey) match {
      case None => state.remove(TestRecap.recapKey)
      case Some(r) =>
        sys.error(s"unexpected recap after passing test run: ${r.map(_.taskName)}")
    }
  }
}
