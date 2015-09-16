package sbt
package internal

import sbt.internal.util.RMap

import java.util.concurrent.ConcurrentHashMap
import TaskName._

/**
 * Measure the time elapsed for running tasks.
 * This class is activated by adding -Dsbt.task.timing=true to the JVM options.
 * Formatting options:
 * - -Dsbt.task.timings.shutdown=true|false
 * - -Dsbt.task.timings.decimals=number
 * - -Dsbt.task.timings.threshold=number
 * @param shutdown    Should the report be given when exiting the JVM (true) or immediatelly (false)?
 */
private[sbt] final class TaskTimings(shutdown: Boolean) extends ExecuteProgress[Task] {
  private[this] val calledBy = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val anonOwners = new ConcurrentHashMap[Task[_], Task[_]]
  private[this] val timings = new ConcurrentHashMap[Task[_], Long]
  private[this] var start = 0L
  private[this] val divider = java.lang.Integer.getInteger("sbt.task.timings.divider", 6).toInt
  private[this] val threshold = java.lang.Long.getLong("sbt.task.timings.threshold", 0L)
  private[this] val unit = divider match {
    case 0 => "ns"
    case 3 => "µs"
    case 6 => "ms"
    case 9 => "sec"
    case _ => ""
  }

  type S = Unit

  if (shutdown) {
    start = System.nanoTime
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() = report()
    })
  }

  def initial = {
    if (!shutdown)
      start = System.nanoTime
  }
  def registered(state: Unit, task: Task[_], allDeps: Iterable[Task[_]], pendingDeps: Iterable[Task[_]]) = {
    pendingDeps foreach { t => if (transformNode(t).isEmpty) anonOwners.put(t, task) }
  }
  def ready(state: Unit, task: Task[_]) = ()
  def workStarting(task: Task[_]) = timings.put(task, System.nanoTime)
  def workFinished[T](task: Task[T], result: Either[Task[T], Result[T]]) = {
    timings.put(task, System.nanoTime - timings.get(task))
    result.left.foreach { t => calledBy.put(t, task) }
  }
  def completed[T](state: Unit, task: Task[T], result: Result[T]) = ()
  def allCompleted(state: Unit, results: RMap[Task, Result]) =
    if (!shutdown) {
      report()
    }

  private[this] def report() = {
    val total = divide(System.nanoTime - start)
    println(s"Total time: $total $unit")
    import collection.JavaConverters._
    def sumTimes(in: Seq[(Task[_], Long)]) = in.map(_._2).sum
    val timingsByName = timings.asScala.toSeq.groupBy { case (t, time) => mappedName(t) } mapValues (sumTimes)
    val times = timingsByName.toSeq.sortBy(_._2).reverse
      .map { case (name, time) => (name, divide(time)) }
      .filter { _._2 > threshold }
    if (times.size > 0) {
      val maxTaskNameLength = times.map { _._1.length }.max
      val maxTime = times.map { _._2 }.max.toString.length
      times.foreach {
        case (taskName, time) =>
          println(s"  ${taskName.padTo(maxTaskNameLength, ' ')}: ${"".padTo(maxTime - time.toString.length, ' ')}$time $unit")
      }
    }
  }
  private[this] def inferredName(t: Task[_]): Option[String] = nameDelegate(t) map mappedName
  private[this] def nameDelegate(t: Task[_]): Option[Task[_]] = Option(anonOwners.get(t)) orElse Option(calledBy.get(t))
  private[this] def mappedName(t: Task[_]): String = definedName(t) orElse inferredName(t) getOrElse anonymousName(t)
  private[this] def divide(time: Long) = (1L to divider.toLong).fold(time) { (a, b) => a / 10L }
}
