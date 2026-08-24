package sbt
package internal

import hedgehog.*
import hedgehog.runner.*
import hedgehog.core.Result
import org.scalasbt.shadedgson.com.google.gson.JsonParser
import java.util.concurrent.atomic.AtomicInteger
// `_root_` because `import hedgehog.*` brings a `hedgehog.sbt` into scope, which would otherwise
// shadow this package. `Task` is aliased because sbt's own task type has the same name here.
import _root_.sbt.util.Logger
import _root_.sbt.testing.{
  Fingerprint,
  Framework,
  Runner,
  Selector,
  SubclassFingerprint,
  Task as STask,
  TaskDef
}

/**
 * Pins the index space that `ForkTests.byFramework` produces.
 *
 * A scripted test cannot reach this: a single-class project never enters the queue path, and a
 * single-framework project gives an identity index mapping, so confusing the full `taskDefs` index
 * space with a per-framework filtered one would go unnoticed.
 */
object ForkTestsFrameworkTest extends Properties:

  override lazy val tests: List[Test] = List(
    example(
      "indices address the full taskDefs vector, not a per-framework subset",
      exFullIndexSpace
    ),
    example("a class matching two frameworks appears in both sub-queues", exRelationNotPartition),
    example("a framework matching nothing gets an empty sub-queue", exNoMatches),
    example("sub-queue order follows the runner sequence", exOrderFollowsRunners),
    example("disjoint sub-queues may be spread", exDisjointSpreads),
    example("a class in two sub-queues stops the group spreading", exOverlapDoesNotSpread),
    example("degenerate shapes are spreadable rather than special-cased", exSpreadableEdges),
    example("one worker, or none allowed, means one JVM", exWorkerCountFloor),
    example("a group that cannot spread never matches fingerprints", exWorkerCountSkipsMatching),
    example("a group that cannot run in parallel keeps to one JVM", exWorkerCountSerial),
    example("a group with an overlapping class keeps to one JVM", exWorkerCountOverlap),
    example("the JVM count is capped by the classes there are to run", exWorkerCountByUnits),
    example("a run that reported everything it was given is complete", exRunComplete),
    example("a class leased and never run makes the run incomplete", exRunLostClass),
    example("a result sbt could not record makes the run incomplete", exRunUnrecorded),
    example("a queue left with work in it fails the group", exUndrainedQueue),
    example("the test request names every class and how to run them", exRequestBody),
  )

  def exRequestBody: Result =
    // One string goes to every worker of a group, so a field missing from it changes the plan in
    // silence: no taskDefs and the worker runs nothing at all and exits 0, which sbt's batch path has
    // no lease accounting to notice; no parallelism and each JVM runs one class at a time whatever
    // the build asked for; and queueMode is what decides whether the worker leases from sbt or just
    // runs the batch it was handed.
    val opts = new Tests.ProcessedOptions(interleaved, Vector.empty, Vector.empty, Vector.empty)
    val json = ForkTests.testRequestJson(
      Seq(junitTf -> noRunner),
      opts,
      // Empty, which is the only reason a null converter is safe here: it is consulted per entry.
      Nil,
      null,
      parallel = true,
      parallelism = Some(3),
      virtualClasspath = false,
      queueMode = true
    )
    val o = JsonParser.parseString(json).getAsJsonObject()
    Result
      .assert(o.getAsJsonArray("taskDefs").size == interleaved.size)
      .and(Result.assert(interleaved.forall(t => json.contains(t.name))))
      .and(Result.assert(o.getAsJsonPrimitive("parallelism").getAsInt == 3))
      .and(Result.assert(o.getAsJsonPrimitive("queueMode").getAsBoolean))
      .and(Result.assert(o.getAsJsonArray("testRunners").size == 1))
      .log(json)

  def exUndrainedQueue: Result =
    // The last thing standing between a class nobody leased and a group that reports a pass.
    Result
      .assert(ForkTests.undrainedQueue(0).isEmpty)
      .and(Result.assert(ForkTests.undrainedQueue(3).exists(_.contains("3 test classes"))))
      .and(Result.assert(ForkTests.undrainedQueue(-1).isEmpty))
      .log(s"three=${ForkTests.undrainedQueue(3)}")

  def exRunComplete: Result =
    Result.assert(ForkTests.incompleteRun(Vector.empty, None).isEmpty)

  def exRunLostClass: Result =
    // Named, and deduplicated: a class matching two frameworks is leased once per framework, and
    // reporting it twice would read as two missing classes.
    val why = ForkTests.incompleteRun(Vector("B", "A", "A"), None)
    Result
      .assert(why.isDefined)
      .and(Result.assert(why.exists(_.endsWith("A, B"))))
      // "A, A, B" would also contain "A, B", so the repeat has to be ruled out on its own.
      .and(Result.assert(!why.exists(_.contains("A, A"))))
      .log(s"why=$why")

  def exRunUnrecorded: Result =
    // Nothing else fails this run: the worker exited cleanly and every lease came back, so without
    // this the group reports a pass with a suite missing from it.
    val why = ForkTests.incompleteRun(Vector.empty, Some("boom"))
    Result
      .assert(why.exists(_.contains("boom")))
      .and(Result.assert(why.exists(_.contains("could not record"))))
      .log(s"why=$why")

  private val twoUnits = Vector(Vector(0, 1, 2, 3), Vector(4, 5))
  private val overlapping = Vector(Vector(0, 1), Vector(1))

  def exWorkerCountFloor: Result =
    // The default. Nothing may talk a group with no budget into forking twice.
    Result
      .assert(ForkTests.workerCount(1, true, twoUnits, Logger.Null) == 1)
      .and(Result.assert(ForkTests.workerCount(0, true, twoUnits, Logger.Null) == 1))
      .and(Result.assert(ForkTests.workerCount(-1, true, twoUnits, Logger.Null) == 1))

  def exWorkerCountSkipsMatching: Result =
    // `units` is by-name, and the budget is checked before anything else, so a group that cannot
    // spread never builds the queue's index map — which means matching every class against every
    // framework's fingerprints. That is every build leaving testForkedWorkStealing alone, so the
    // early return is the whole reason the default path costs nothing.
    val forced = AtomicInteger(0)
    val workers =
      ForkTests.workerCount(1, true, { forced.incrementAndGet(); twoUnits }, Logger.Null)
    Result
      .assert(workers == 1)
      .and(Result.assert(forced.get() == 0))
      .log(s"workers=$workers forced=${forced.get()}")

  def exWorkerCountSerial: Result =
    // Spreading a group is parallel execution however the threads inside each JVM are configured, so
    // a build that turned parallel execution off must not get it by another name.
    Result.assert(ForkTests.workerCount(4, false, twoUnits, Logger.Null) == 1)

  def exWorkerCountOverlap: Result =
    Result
      .assert(ForkTests.workerCount(4, true, overlapping, Logger.Null) == 1)
      .and(Result.assert(ForkTests.workerCount(4, true, twoUnits, Logger.Null) == 4))

  def exWorkerCountByUnits: Result =
    // Six work units and a budget of four is four JVMs; two units and a budget of four is two, since
    // a JVM with nothing to lease only costs a fork.
    Result
      .assert(ForkTests.workerCount(4, true, twoUnits, Logger.Null) == 4)
      .and(Result.assert(ForkTests.workerCount(8, true, twoUnits, Logger.Null) == 6))
      .and(Result.assert(ForkTests.workerCount(4, true, Vector(Vector(0, 1)), Logger.Null) == 2))
      .and(Result.assert(ForkTests.workerCount(4, true, Vector(Vector.empty), Logger.Null) == 1))

  // Otherwise the two runs of a class matching two frameworks could overlap in different JVMs, and
  // two writers land on one TEST-<suite>.xml.
  def exDisjointSpreads: Result =
    Result
      .assert(ForkTests.spreadable(Vector(Vector(0, 2, 4), Vector(1, 3))))
      .and(Result.assert(ForkTests.spreadable(Vector(Vector(0, 1, 2)))))

  def exOverlapDoesNotSpread: Result =
    Result
      .assert(!ForkTests.spreadable(Vector(Vector(0, 1, 2, 3, 4), Vector(1, 3))))
      // One shared class out of many is still enough: the queue's unit is a (framework, class) pair
      // and nothing can pin two of them to the same worker.
      .and(Result.assert(!ForkTests.spreadable(Vector(Vector(0, 1, 2, 3), Vector(3)))))

  def exSpreadableEdges: Result =
    Result
      .assert(ForkTests.spreadable(Vector.empty))
      .and(Result.assert(ForkTests.spreadable(Vector(Vector.empty))))
      // A framework matching nothing cannot overlap with anything.
      .and(Result.assert(ForkTests.spreadable(Vector(Vector(0, 1), Vector.empty))))

  private def print(superclass: String): SubclassFingerprint =
    new SubclassFingerprint:
      def isModule(): Boolean = false
      def superclassName(): String = superclass
      def requireNoArgConstructor(): Boolean = false

  private val junitPrint = print("junit.framework.TestCase")
  private val specsPrint = print("org.specs2.Specification")

  private def framework(nm: String, prints: Fingerprint*): Framework =
    new Framework:
      def name(): String = nm
      def fingerprints(): Array[Fingerprint] = prints.toArray
      def runner(args: Array[String], remoteArgs: Array[String], cl: ClassLoader): Runner =
        sys.error("not used")

  private val noRunner: Runner =
    new Runner:
      def args(): Array[String] = Array.empty
      def remoteArgs(): Array[String] = Array.empty
      def tasks(defs: Array[TaskDef]): Array[STask] = Array.empty[STask]
      def done(): String = ""

  private def defn(nm: String, print: Fingerprint): TestDefinition =
    new TestDefinition(nm, print, false, Array.empty[Selector])

  private val junitTf = TestFramework("junit.Framework")
  private val specsTf = TestFramework("specs.Framework")

  /** junit and specs2 suites interleaved, so neither framework's indices are contiguous. */
  private val interleaved: Vector[TestDefinition] = Vector(
    defn("A", junitPrint),
    defn("B", specsPrint),
    defn("C", junitPrint),
    defn("D", specsPrint),
    defn("E", junitPrint),
  )

  def exFullIndexSpace: Result =
    val runnerSeq = Seq(junitTf -> noRunner, specsTf -> noRunner)
    val frameworks = Map(
      junitTf -> framework("junit", junitPrint),
      specsTf -> framework("specs2", specsPrint),
    )
    val queues = ForkTests.byFramework(runnerSeq, frameworks, interleaved)
    Result
      .assert(queues.length == 2)
      .and(Result.assert(queues(0) == Vector(0, 2, 4)))
      .and(Result.assert(queues(1) == Vector(1, 3)))
      .log(s"queues=$queues")

  def exRelationNotPartition: Result =
    val both = framework("both", junitPrint, specsPrint)
    val runnerSeq = Seq(junitTf -> noRunner, specsTf -> noRunner)
    val frameworks = Map(junitTf -> both, specsTf -> framework("specs2", specsPrint))
    val queues = ForkTests.byFramework(runnerSeq, frameworks, interleaved)
    Result
      .assert(queues(0) == Vector(0, 1, 2, 3, 4))
      .and(Result.assert(queues(1) == Vector(1, 3)))
      .and(Result.assert(queues(0).intersect(queues(1)) == Vector(1, 3)))
      .log(s"queues=$queues")

  def exNoMatches: Result =
    val other = framework("other", print("nothing.Matches"))
    val runnerSeq = Seq(junitTf -> noRunner)
    val queues = ForkTests.byFramework(runnerSeq, Map(junitTf -> other), interleaved)
    Result.assert(queues == Vector(Vector.empty)).log(s"queues=$queues")

  def exOrderFollowsRunners: Result =
    val frameworks = Map(
      junitTf -> framework("junit", junitPrint),
      specsTf -> framework("specs2", specsPrint),
    )
    val forward =
      ForkTests.byFramework(Seq(junitTf -> noRunner, specsTf -> noRunner), frameworks, interleaved)
    val reversed =
      ForkTests.byFramework(Seq(specsTf -> noRunner, junitTf -> noRunner), frameworks, interleaved)
    Result
      .assert(forward == Vector(Vector(0, 2, 4), Vector(1, 3)))
      .and(Result.assert(reversed == Vector(Vector(1, 3), Vector(0, 2, 4))))
      .log(s"forward=$forward reversed=$reversed")
end ForkTestsFrameworkTest
