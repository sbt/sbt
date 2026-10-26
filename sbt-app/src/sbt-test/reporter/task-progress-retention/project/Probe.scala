package sbt.taskprogressretention

import java.util.concurrent.{ ConcurrentLinkedQueue, CountDownLatch, TimeUnit }
import java.util.concurrent.atomic.{ AtomicInteger, AtomicReference }
import sbt.*
import sbt.Keys.*
import sbt.internal.util.{ Appender, ManagedLogger }
import sbt.util.{ Level, LoggerContext }
import scala.jdk.CollectionConverters.*

object Probe:
  val dependency = taskKey[Unit]("A dependency that the probe also returns after it completed")
  val probe = taskKey[Unit]("Records the live logger and task progress of one evaluation")
  val cancelled = taskKey[Unit]("Waits for a sentinel task that another task cancels")

  final case class Observation(
      logger: String,
      appender: Appender,
      context: LoggerContext,
      progress: sbt.internal.TaskProgress
  )

  private val evaluations = 20
  private val metadata = Set("active", "anonOwners", "calledBy", "taskNameCache", "timings")
  private val dependencyRuns = new AtomicInteger
  private val observations = new ConcurrentLinkedQueue[Observation]
  private val sentinelStarted = new AtomicReference(new CountDownLatch(1))

  def settings: Seq[Setting[?]] = Seq(
    dependency := Def.uncached(countDependency()),
    probe := Def.uncached(probeTask.value),
    cancelled := Def.uncached(cancelledTask.value),
    commands ++= Seq(evaluateRepeatedly, evaluateCancelled),
  )

  private def countDependency(): Unit =
    dependencyRuns.incrementAndGet()
    ()

  private def probeTask: Def.Initialize[Task[Unit]] = Def.taskDyn:
    dependency.value
    val s = state.value
    observe(streams.value.log, s)
    awaitProgress(s)
    dependency

  private val sentinel: Def.Initialize[Task[Unit]] = Def
    .task:
      sentinelStarted.get.countDown()
      new CountDownLatch(1).await()
    .tag(Tags.Sentinel)

  private val cancelSentinel: Def.Initialize[Task[Unit]] = Def.task:
    assert(sentinelStarted.get.await(10, TimeUnit.SECONDS), "the sentinel did not start")
    ConcurrentRestrictions.cancelAllSentinels()

  private def cancelledTask: Def.Initialize[Task[Unit]] = Def.task:
    sentinel.value
    cancelSentinel.value

  private def progressOf(s: State): sbt.internal.TaskProgress =
    s.get(Keys.taskProgress).getOrElse(sys.error("no task progress in task state"))

  private def observe(log: ManagedLogger, s: State): Unit =
    val context = s.get(Keys.loggerContext).getOrElse(sys.error("no logger context in task state"))
    val progress = progressOf(s)
    val appenders = context.appenders(log.name)
    assert(appenders.nonEmpty, s"${log.name} is not registered in the logger context")
    assert(progress.currentTimings.nonEmpty, "task progress does not time the running probe")
    observations.add(Observation(log.name, appenders.head, context, progress))

  private def unregistered(context: LoggerContext, name: String, probe: Appender): Boolean =
    val cleared = context.appenders(name).isEmpty
    context.addAppender(name, probe -> Level.Info)
    cleared && context.appenders(name).isEmpty

  private def awaitProgress(s: State): Unit = progressOf(s).awaitIdle()

  private def retained(progress: sbt.internal.TaskProgress): Map[String, Int] =
    val maps = progress.retainedTaskMetadata
    assert(maps.keySet == metadata, s"unexpected task progress metadata: ${maps.keySet}")
    maps.collect:
      case (name, tasks) if tasks.nonEmpty => name -> tasks.size

  private def evaluateRepeatedly = Command.command("evaluateRepeatedly"): s =>
    val context = s.get(Keys.loggerContext).getOrElse(sys.error("no logger context"))
    val progress = s.get(Keys.taskProgress).getOrElse(sys.error("no task progress"))
    val extracted = Project.extract(s)
    dependencyRuns.set(0)
    observations.clear()
    (1 to evaluations).foreach: i =>
      extracted.runTask(probe, s)
      val all = observations.asScala.toVector
      assert(all.size == i, s"evaluation $i ran the probe body ${all.size} times in total")
      val o = all.last
      assert(o.context eq context, s"evaluation $i used another logger context")
      assert(o.progress eq progress, s"evaluation $i used another task progress")
      assert(
        unregistered(context, o.logger, o.appender),
        s"evaluation $i left logger ${o.logger} registered"
      )
      val left = retained(progress)
      assert(left.isEmpty, s"evaluation $i left task progress metadata: $left")
    assert(dependencyRuns.get == evaluations, s"dependency ran ${dependencyRuns.get} times")
    s

  private def evaluateCancelled = Command.command("evaluateCancelled"): s =>
    val progress = progressOf(s)
    val extracted = Project.extract(s)
    (1 to evaluations).foreach: i =>
      sentinelStarted.set(new CountDownLatch(1))
      val result = extracted.runTaskUnhandled(cancelled, s).map(_._2)
      val wasCancelled = result.exists:
        case Result.Inc(inc) => inc.message.contains("cancelled")
        case _               => false
      assert(wasCancelled, s"evaluation $i was not cancelled: $result")
      val left = retained(progress)
      assert(left.isEmpty, s"cancelled evaluation $i left task progress metadata: $left")
    s
end Probe
