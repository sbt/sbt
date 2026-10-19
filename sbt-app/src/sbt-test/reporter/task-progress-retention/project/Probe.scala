package sbt.taskprogressretention

import java.util.concurrent.{ ConcurrentLinkedQueue, ExecutorService, TimeUnit }
import java.util.concurrent.atomic.AtomicInteger
import sbt.*
import sbt.Keys.*
import sbt.internal.util.{ Appender, ManagedLogger }
import sbt.util.{ Level, LoggerContext }
import scala.jdk.CollectionConverters.*

object Probe:
  val dependency = taskKey[Unit]("A dependency that the probe also returns after it completed")
  val probe = taskKey[Unit]("Records the live logger and task progress of one evaluation")

  final case class Observation(
      logger: String,
      appender: Appender,
      context: LoggerContext,
      progress: AnyRef
  )

  private val evaluations = 20
  private val metadata = Set("active", "anonOwners", "calledBy", "taskNameCache", "timings")
  private val dependencyRuns = new AtomicInteger
  private val observations = new ConcurrentLinkedQueue[Observation]

  def settings: Seq[Setting[?]] = Seq(
    dependency := Def.uncached(countDependency()),
    probe := Def.uncached(probeTask.value),
    commands += evaluateRepeatedly,
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

  private def fields(progress: AnyRef): Map[String, AnyRef] =
    Iterator
      .iterate[Class[?]](progress.getClass)(_.getSuperclass)
      .takeWhile(_ != null)
      .flatMap(_.getDeclaredFields)
      .map: field =>
        field.setAccessible(true)
        field.getName.split("\\$").last -> field.get(progress)
      .toMap

  private def awaitProgress(s: State): Unit =
    fields(progressOf(s)).get("executor") match
      case Some(executor: ExecutorService) =>
        executor.submit((() => ()): Runnable).get(10, TimeUnit.SECONDS)
      case other => sys.error(s"task progress executor not found: $other")

  private def retained(progress: AnyRef): Map[String, Int] =
    val maps = fields(progress).collect:
      case (name, map: java.util.Map[?, ?]) if metadata(name) => name -> map.size
    assert(maps.keySet == metadata, s"task progress fields not found: ${metadata -- maps.keySet}")
    maps.filter(_._2 > 0)

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
end Probe
