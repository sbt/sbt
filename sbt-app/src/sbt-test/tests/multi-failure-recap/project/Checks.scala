package sbt
package multifailurerecap

import sbt.internal.testing.TestRecap

/**
 * Lives in package `sbt` so it can access `TestRecap`, which is `private[sbt]`.
 *
 * Caveat: a scripted statement that fails (`-> test`) closes the inner sbt's
 * IPC server (`SbtHandler.onNewSbtInstance`'s catch block calls `finish`),
 * which terminates the inner sbt JVM. scripted then launches a fresh JVM
 * for the next statement. Empirically we confirmed via
 * `ManagementFactory.getRuntimeMXBean.getName` that the PID differs across
 * the `-> test` boundary, so the `State` attribute Aggregation puts on
 * failure cannot be read by a follow-up `> check` statement.
 *
 * These helpers are kept for documentation; verification of recap content
 * still relies on running the aggregated test in the same sbt invocation.
 */
object Checks {
  def checkRecap(state: State): Unit = {
    val recap = state.get(TestRecap.recapKey).getOrElse {
      sys.error("TestRecap.recapKey not present on state after aggregated test failure")
    }
    val names = recap.map(_.taskName).toSet
    assert(recap.size == 2, s"expected 2 failures, got ${recap.size}: $names")
    assert(names.contains("a / Test / test"), s"recap missing project a: $names")
    assert(names.contains("c / Test / test"), s"recap missing project c: $names")
    assert(!names.contains("b / Test / test"), s"recap should not list project b: $names")
  }

  def checkNoRecap(state: State): Unit =
    state.get(TestRecap.recapKey) match {
      case None    => ()
      case Some(_) => sys.error("recap state attribute should be empty after success")
    }
}
