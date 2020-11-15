/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import javax.management.{ NotificationEmitter, NotificationListener }
import java.lang.management.ManagementFactory
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicReference
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try
import sbt.util.Logger

trait GCMonitorBase {
  protected def window: FiniteDuration

  protected def ratio: Double

  protected val queue = new LinkedBlockingQueue[(FiniteDuration, Long)]
  protected val queueScala = queue.asScala
  private[this] val lastWarned = new AtomicReference(Deadline(Int.MinValue.millis))

  protected def emitWarning(total: Long, over: Option[Long]): Unit

  val startTime = System.currentTimeMillis()

  private[internal] def totalCollectionTimeChanged(
      nowMillis: Long,
      collectionTime: Long,
      collectionTimeStore: AtomicReference[(FiniteDuration, Long)]
  ): Unit = {
    val now = nowMillis.millis
    val (lastTimestamp, lastCollectionTime) = collectionTimeStore.getAndSet(now -> collectionTime)
    val elapsed = collectionTime - lastCollectionTime
    queue.removeIf { case (whenEventHappened, _) => now > whenEventHappened + window }
    queue.add(lastTimestamp -> elapsed)
    val total = queueScala.foldLeft(0L) { case (total, (_, t)) => total + t }
    val over = queueScala.headOption.map(nowMillis - _._1.toMillis)
    if ((total > window.toMillis * ratio) && (lastWarned.get + window).time <= now) {
      lastWarned.set(Deadline(now))
      emitWarning(total, over)
    }
  }
}

class GCMonitor(logger: Logger) extends GCMonitorBase with AutoCloseable {
  override protected def window =
    Try(System.getProperty("sbt.gc.monitor.window", "10").toInt).getOrElse(10).seconds

  override protected def ratio =
    Try(System.getProperty("sbt.gc.monitor.ratio", "0.5").toDouble).getOrElse(0.5)

  final val GB = 1024 * 1024 * 1024.0
  val runtime = Runtime.getRuntime
  def gbString(n: Long) = f"${n / GB}%.2fGB"

  override protected def emitWarning(total: Long, over: Option[Long]): Unit = {
    val totalSeconds = total / 1000.0
    val amountMsg = over.fold(totalSeconds + " seconds") { d =>
      "In the last " + (d / 1000.0).ceil.toInt + f" seconds, $totalSeconds (${total.toDouble / d * 100}%.1f%%)"
    }
    val msg = s"$amountMsg were spent in GC. " +
      s"[Heap: ${gbString(runtime.freeMemory())} free " +
      s"of ${gbString(runtime.totalMemory())}, " +
      s"max ${gbString(runtime.maxMemory())}] " +
      "Consider increasing the JVM heap using `-Xmx` or try " +
      "a different collector, e.g. `-XX:+UseG1GC`, for better performance."
    logger.warn(msg)
  }

  val removers = ManagementFactory.getGarbageCollectorMXBeans.asScala.flatMap {
    case bean: NotificationEmitter =>
      val collectionTimeStore =
        new AtomicReference(System.currentTimeMillis().millis -> bean.getCollectionTime)
      val listener: NotificationListener =
        (notification, _) =>
          totalCollectionTimeChanged(
            notification.getTimeStamp,
            bean.getCollectionTime,
            collectionTimeStore
          )
      bean.addNotificationListener(listener, null, null)
      Some(() => bean.removeNotificationListener(listener))
    case _ => None
  }
  override def close(): Unit = {
    removers.foreach(_.apply())
  }
}
