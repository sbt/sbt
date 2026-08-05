package sbt

import org.scalacheck.*
import org.scalacheck.Prop.*

/**
 * Pins [[Defaults.stealingWorkers]] and [[Tags.maxAllowed]].
 *
 * How far a group may spread is not a setting — it is read off the `concurrentRestrictions` in
 * force, so the rule that governs the forked test JVMs also sizes the fan-out and the two cannot
 * disagree.
 */
object StealingWorkersTest extends Properties("stealingWorkers") {

  private val cores = EvaluateTask.SystemProcessors

  private def rules(forkedLimit: Int): Seq[Tags.Rule] =
    Seq(
      Tags.limitAll(cores),
      Tags.limit(Tags.ForkedTestGroup, forkedLimit),
      Tags.exclusiveGroup(Tags.Clean)
    )

  property("off means one JVM, whatever the rules allow") = forAll(Gen.choose(1, 64)) { (n: Int) =>
    Defaults.stealingWorkers(stealing = false, rules(n)) == 1
  }

  property("on means however many the rules admit") = forAll(Gen.choose(1, 64)) { (n: Int) =>
    Defaults.stealingWorkers(stealing = true, rules(n)) == math.min(n, cores)
  }

  property("sbt's own default rule set gives one") = {
    // Tags.limit(ForkedTestGroup, 1) is what defaultRestrictions has always carried, so a build
    // that leaves concurrentRestrictions alone never spreads a group however the flag is set.
    Prop(Defaults.stealingWorkers(stealing = true, rules(1)) == 1)
  }

  property("a rule that admits nothing still leaves one JVM") = {
    // Rather than zero workers, which would build no tasks and run no tests. Tags.limit refuses a
    // max below one (`checkMax`), so reaching this needs a custom rule.
    Prop(Defaults.stealingWorkers(stealing = true, Seq(Tags.customLimit(_ => false))) == 1)
  }

  property("the tighter of two rules on the tag wins") = forAll(Gen.choose(1, 32)) { (n: Int) =>
    // Which is why `+=` cannot raise the limit: rules are anded together.
    val anded = rules(1) :+ Tags.limit(Tags.ForkedTestGroup, n)
    Defaults.stealingWorkers(stealing = true, anded) == 1
  }

  property("limitAll caps it too, since tagged tasks are also tasks") = {
    val generous = Seq(Tags.limitAll(2), Tags.limit(Tags.ForkedTestGroup, 64))
    Prop(Defaults.stealingWorkers(stealing = true, generous) == 2)
  }

  property("a limitAll above the core count is honoured, not clamped to it") = {
    // The rules are what govern the JVMs, so a build deliberately oversubscribing its cores — an
    // IO-bound suite, say — gets the fan-out it asked for rather than one per core.
    val oversubscribed =
      Seq(Tags.limitAll(cores * 4), Tags.limit(Tags.ForkedTestGroup, cores * 2))
    Prop(Defaults.stealingWorkers(stealing = true, oversubscribed) == cores * 2)
  }

  property("a rule making forked test groups exclusive gives one") = {
    // Not the same rule as exclusiveGroup: this one admits a second task only if it is untagged, so
    // a group that spread would break the isolation the build asked for.
    Prop(Defaults.stealingWorkers(stealing = true, Seq(Tags.exclusive(Tags.ForkedTestGroup))) == 1)
  }

  property("a limit with a hole in it stops at the hole") = {
    // The engine reaches n by running n-1 first, so a count is only usable when every count below it
    // is admitted too. Taking the largest admitted anywhere would spread through a level the rules
    // forbid — the probe has to stop at the first refusal, not look past it.
    val holed = Tags.customLimit(_.getOrElse(Tags.ForkedTestGroup, 0) != 3)
    Prop(Tags.maxAllowed(Seq(holed), Tags.ForkedTestGroup, 64) == 2)
  }

  property("rules naming no total limit fall back to the core count") = {
    // Probing cannot tell an unbounded rule set from a very large one, and the alternative is a
    // forked JVM per test class.
    Prop(Defaults.stealingWorkers(stealing = true, Nil) == cores)
      .&&(
        Prop(
          Defaults.stealingWorkers(stealing = true, Seq(Tags.exclusiveGroup(Tags.Clean)))
            == cores
        )
      )
  }
}
