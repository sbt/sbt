package sbt.internal.worker1

import sbt.testing.*
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A framework whose only job is to record which classes a run was handed, so a test can drive
 * [[ForkTestMain]]'s queue mode against it. Loaded by name from the worker, as a real one is.
 */
object StealingFramework:
  /** Classes executed, in completion order, across every run in this JVM. */
  val executed: ConcurrentLinkedQueue[String] = ConcurrentLinkedQueue()

  /** Set to make that class's task throw, standing in for a test that dies mid-run. */
  @volatile var failOn: Option[String] = None

  /** Set to make `tasks` throw for that class, standing in for a framework that cannot load it. */
  @volatile var failTasksOn: Option[String] = None

  /**
   * Set to make that class's task refuse to say what it is. The worker asks before it starts
   * guarding the run, so this fails the task's future rather than becoming a reported test error --
   * the same place writing a group's start or end would fail.
   */
  @volatile var failTaskDefOn: Option[String] = None

  /**
   * Classes whose task returns one nested task, keyed by class name. This is how ScalaTest and
   * specs2 hand a nested suite back to the runner rather than running it themselves.
   */
  @volatile var nestedOf: Map[String, String] = Map.empty

  /** Times `Runner.done` was called, which is where a framework flushes its run summary. */
  val doneCalls: AtomicInteger = AtomicInteger(0)

  private val running = AtomicInteger(0)

  /** The most classes this JVM ever had under way at once. */
  val peak: AtomicInteger = AtomicInteger(0)

  /** Held long enough for an overlapping run to be visible in `peak`. */
  private def occupy(): Unit =
    val now = running.incrementAndGet()
    peak.getAndUpdate(p => math.max(p, now))
    Thread.sleep(50)
    running.decrementAndGet()
    ()

  val print: SubclassFingerprint = new SubclassFingerprint:
    def isModule(): Boolean = false
    def superclassName(): String = "junit.framework.TestCase"
    def requireNoArgConstructor(): Boolean = false

  def reset(): Unit =
    executed.clear()
    failOn = None
    failTasksOn = None
    failTaskDefOn = None
    nestedOf = Map.empty
    doneCalls.set(0)
    running.set(0)
    peak.set(0)

class StealingFramework extends Framework:
  def name(): String = "stealing"
  def fingerprints(): Array[Fingerprint] = Array(StealingFramework.print)
  def runner(args: Array[String], remoteArgs: Array[String], loader: ClassLoader): Runner =
    new Runner:
      def args(): Array[String] = Array.empty
      def remoteArgs(): Array[String] = Array.empty
      def done(): String =
        StealingFramework.doneCalls.incrementAndGet()
        ""
      def tasks(defs: Array[TaskDef]): Array[Task] =
        defs
          .find(d => StealingFramework.failTasksOn.contains(d.fullyQualifiedName()))
          .foreach: d =>
            throw RuntimeException(s"${d.fullyQualifiedName()} cannot be loaded")
        defs.map(task)

  private def task(td: TaskDef): Task = new Task:
    def taskDef(): TaskDef =
      if StealingFramework.failTaskDefOn.contains(td.fullyQualifiedName()) then
        throw RuntimeException(s"${td.fullyQualifiedName()} will not say what it is")
      td
    def tags(): Array[String] = Array.empty
    def execute(handler: EventHandler, loggers: Array[Logger]): Array[Task] =
      val name = td.fullyQualifiedName()
      if StealingFramework.failOn.contains(name) then
        throw RuntimeException(s"$name was told to fail")
      StealingFramework.occupy()
      StealingFramework.executed.add(name)
      handler.handle(new Event:
        def fullyQualifiedName(): String = name
        def fingerprint(): Fingerprint = StealingFramework.print
        def selector(): Selector = SuiteSelector()
        def status(): Status = Status.Success
        def throwable(): OptionalThrowable = OptionalThrowable()
        def duration(): Long = 1L)
      // Handed back for the runner to execute, not run here — the worker has to walk into it.
      StealingFramework.nestedOf.get(name) match
        case Some(child) =>
          Array(task(TaskDef(child, StealingFramework.print, false, Array(SuiteSelector()))))
        case None => Array.empty
