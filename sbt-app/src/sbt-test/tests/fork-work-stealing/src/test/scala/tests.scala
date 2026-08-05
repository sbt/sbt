
import java.io.File
import org.junit.Test
import sbt.testing.{
  Event,
  EventHandler,
  Fingerprint,
  Framework,
  Logger,
  OptionalThrowable,
  Runner,
  Selector,
  Status,
  SubclassFingerprint,
  SuiteSelector,
  Task,
  TaskDef
}

/**
 * Each suite records the JVM it ran in by creating an empty file named `<suite>.<pid>`.
 *
 * No sleeps and no timestamps: every assertion in build.sbt is about which suite ran in which
 * process, which the queue fixes rather than a race.
 */
object Recorder {
  private def pid: String = {
    val vm = java.lang.management.ManagementFactory.getRuntimeMXBean().getName()
    vm.takeWhile(_ != '@')
  }

  def record(suite: String): Unit = {
    val dir = new File("pids")
    dir.mkdirs()
    new File(dir, s"$suite.$pid").createNewFile()
    ()
  }

  private def recordedNames: Array[String] =
    Option(new File("pids").listFiles()).getOrElse(Array.empty[File]).map(_.getName)

  private def recordedPids: Set[String] =
    recordedNames.map(n => n.substring(n.lastIndexOf('.') + 1)).toSet

  /**
   * Waits for a second class to be running in this same JVM, where the scenario asked a worker for
   * more than one thread.
   *
   * Every other scenario leaves `testForkedParallelism` at None, which is one class at a time per
   * JVM, so this is the only place a group has several suites open on one connection's reader thread
   * -- what keying the listener's open suites by name is for. A worker that ran them one at a time
   * would satisfy every other assertion here.
   *
   * Neither class can leave until both have arrived, so the second arrival proves they overlapped
   * rather than merely both having happened. Later classes in the same JVM pass at once on the
   * earlier markers, which is fine: the first pair is the proof. The bound is a backstop -- both
   * threads hold a lease while they wait, so the sibling is already running.
   */
  def awaitSibling(): Unit =
    if (new File("expect-threads").exists) {
      def mine: Int = recordedNames.count(_.endsWith("." + pid))
      val deadline = System.currentTimeMillis() + 60000L
      while (mine < 2 && System.currentTimeMillis() < deadline) Thread.sleep(50)
      if (mine < 2)
        throw new AssertionError(
          s"expected this JVM ($pid) to be running two classes at once, saw only ${mine}: " +
            recordedNames.mkString(", ")
        )
    }

  /**
   * Waits for a peer JVM to record work, in the scenarios that expect the group to be spread.
   *
   * Without this the whole project passes with work stealing disabled: one JVM running all six
   * classes satisfies every other assertion here. Holding this class also pins its JVM without
   * draining the queue — a worker runs one class at a time — so the peer sbt admits still has
   * something to claim.
   *
   * The bound is a backstop, not a timing assertion: this class blocks holding its lease while the
   * queue still has work, so the peer's marker is at most one fork's startup away.
   */
  def awaitSpread(): Unit =
    if (new File("expect-spread").exists) {
      val want = System.getProperty("expect.jvms").toInt
      val deadline = System.currentTimeMillis() + 60000L
      while (recordedPids.size < want && System.currentTimeMillis() < deadline) Thread.sleep(50)
      if (recordedPids.size < want)
        throw new AssertionError(
          s"expected this group to be spread over $want JVMs, but only $recordedPids recorded anything"
        )
    }
}

class A {
  @Test def t(): Unit = {
    Recorder.record("A")
    // Kills this worker only in the crash scenario, after the record above is on disk and the
    // startTestGroup notification is written.
    if (new File("mode-crash").exists) System.exit(1)
    Recorder.awaitSibling()
    Recorder.awaitSpread()
  }
}

// awaitSibling on every class, not just A: which classes share a JVM is up to the queue, so any of
// them may be one of the pair that has to overlap.
class B { @Test def t(): Unit = { Recorder.record("B"); Recorder.awaitSibling() } }
class C { @Test def t(): Unit = { Recorder.record("C"); Recorder.awaitSibling() } }
class D { @Test def t(): Unit = { Recorder.record("D"); Recorder.awaitSibling() } }

/**
 * A second, deliberately minimal framework.
 *
 * Two frameworks give the queue two sub-queues, so the indices it hands out have to address the
 * full class list rather than one framework's slice of it. Hand-written rather than resolved, since
 * a real second framework would cost a dependency resolution for what these few classes cover.
 */
trait Marked

final class MarkedFramework extends Framework {
  def name(): String = "marked"
  def fingerprints(): Array[Fingerprint] = Array(
    new SubclassFingerprint {
      def isModule(): Boolean = false
      def superclassName(): String = "Marked"
      def requireNoArgConstructor(): Boolean = true
    }
  )
  def runner(args: Array[String], remoteArgs: Array[String], cl: ClassLoader): Runner =
    new MarkedRunner
}

/**
 * A third framework carrying the *same* fingerprint as MarkedFramework, so E and F match two
 * frameworks at once and become two work units each. Only the overlap scenario adds it, with `set`.
 */
final class MarkedFramework2 extends Framework {
  def name(): String = "marked2"
  def fingerprints(): Array[Fingerprint] = Array(
    new SubclassFingerprint {
      def isModule(): Boolean = false
      def superclassName(): String = "Marked"
      def requireNoArgConstructor(): Boolean = true
    }
  )
  def runner(args: Array[String], remoteArgs: Array[String], cl: ClassLoader): Runner =
    new MarkedRunner
}

final class MarkedRunner extends Runner {
  def args(): Array[String] = Array.empty
  def remoteArgs(): Array[String] = Array.empty
  def done(): String = ""
  def tasks(defs: Array[TaskDef]): Array[Task] = defs.map(d => new MarkedTask(d): Task)
}

final class MarkedTask(d: TaskDef) extends Task {
  def taskDef(): TaskDef = d
  def tags(): Array[String] = Array.empty
  def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] = {
    val suite = d.fullyQualifiedName()
    Recorder.record(suite)
    Recorder.awaitSibling()
    handler.handle(new Event {
      def fullyQualifiedName(): String = suite
      def fingerprint(): Fingerprint = d.fingerprint()
      def selector(): Selector = new SuiteSelector
      def status(): Status = Status.Success
      def throwable(): OptionalThrowable = new OptionalThrowable
      def duration(): Long = 0L
    })
    Array.empty
  }
}

class E extends Marked
class F extends Marked
