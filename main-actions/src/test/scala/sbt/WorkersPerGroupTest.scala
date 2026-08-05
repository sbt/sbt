package sbt

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.Result
import _root_.sbt.testing.{ Fingerprint, Selector, SubclassFingerprint }

/**
 * Pins [[Tests.workersPerGroup]].
 *
 * The forked-JVM ceiling is a budget for a project's test run, and every group gets its own queue
 * and its own fan-out, so handing each the whole number multiplies JVM launches by the group count
 * rather than the concurrency, which the `ForkedTestGroup` rule caps either way.
 */
object WorkersPerGroupTest extends Properties:

  override lazy val tests: List[Test] = List(
    example("one group gets the whole budget", exOneGroup),
    example("the budget is shared, not handed to each group", exShared),
    example("more groups than budget still leaves every group a worker", exNeverZero),
    example("groups that will not fork are not charged a share", exInProcessNotCharged),
    example("groups left empty by filtering are not charged a share", exEmptyNotCharged),
    example("a budget of one is one per group, whatever the grouping", exDefaultUnchanged),
  )

  private val print: SubclassFingerprint =
    new SubclassFingerprint:
      def isModule(): Boolean = false
      def superclassName(): String = "junit.framework.TestCase"
      def requireNoArgConstructor(): Boolean = false

  private def defn(nm: String): TestDefinition =
    new TestDefinition(nm, print: Fingerprint, false, Array.empty[Selector])

  private def group(nm: String, forked: Boolean, n: Int): Tests.Group =
    new Tests.Group(
      nm,
      (0 until n).map(i => defn(s"$nm$i")),
      if forked then Tests.SubProcess(ForkOptions()) else Tests.InProcess
    )

  /** As `allTestGroupsTask` builds it: each group mapped to the tests left after filtering. */
  private def processed(gs: Seq[Tests.Group]): Map[Tests.Group, Tests.ProcessedOptions] =
    gs.map(g =>
      g -> Tests.ProcessedOptions(g.tests.toVector, Vector.empty, Vector.empty, Vector.empty)
    ).toMap

  private def workers(budget: Int, gs: Seq[Tests.Group]): Int =
    Tests.workersPerGroup(budget, gs, processed(gs))

  def exOneGroup: Result =
    val gs = Seq(group("g0", forked = true, 20))
    Result.assert(workers(4, gs) == 4).log(s"got ${workers(4, gs)}")

  def exShared: Result =
    val two = Seq(group("g0", true, 10), group("g1", true, 10))
    val four = (0 until 4).map(i => group(s"g$i", true, 5))
    Result
      .assert(workers(4, two) == 2)
      .and(Result.assert(workers(4, four) == 1))
      // The product is what matters: budget × 1 group and (budget/n) × n groups are the same total.
      .and(Result.assert(workers(4, two) * 2 == 4))
      .and(Result.assert(workers(4, four) * 4 == 4))
      .log(s"two=${workers(4, two)} four=${workers(4, four)}")

  def exNeverZero: Result =
    val eight = (0 until 8).map(i => group(s"g$i", true, 2))
    // 4/8 truncates to 0, which would build no worker tasks and run nothing at all.
    Result.assert(workers(4, eight) == 1).log(s"got ${workers(4, eight)}")

  def exInProcessNotCharged: Result =
    val mixed = Seq(group("g0", forked = true, 10), group("g1", forked = false, 10))
    Result.assert(workers(4, mixed) == 4).log(s"got ${workers(4, mixed)}")

  def exEmptyNotCharged: Result =
    // What `testOnly` against one group of four produces: the other three fork nothing, so charging
    // them a share would leave the one group with work a quarter of the budget.
    val gs = Seq(group("g0", true, 5), group("g1", true, 0), group("g2", true, 0))
    Result.assert(workers(4, gs) == 4).log(s"got ${workers(4, gs)}")

  def exDefaultUnchanged: Result =
    // A budget of one is one JVM per group however the project is grouped, which is what sbt has
    // always done — so the default cannot change how an existing build runs its tests.
    val counts = Seq(1, 2, 4, 16).map(n => workers(1, (0 until n).map(i => group(s"g$i", true, 5))))
    Result.assert(counts.forall(_ == 1)).log(s"got $counts")
end WorkersPerGroupTest
