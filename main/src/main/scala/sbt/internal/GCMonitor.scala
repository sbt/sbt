/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import javax.management.{ Notification, NotificationEmitter, NotificationListener }
import java.lang.management.ManagementFactory
import java.lang.management.GarbageCollectorMXBean
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.{ AtomicLong, AtomicReference }
import scala.concurrent.duration._
import scala.collection.JavaConverters._
import scala.util.Try
import sbt.util.Logger

class GCMonitor(logger: Logger) extends AutoCloseable {
  private[this] val window =
    Try(System.getProperty("sbt.gc.monitor.window", "10").toInt).getOrElse(10).seconds
  private[this] val ratio =
    Try(System.getProperty("sbt.gc.monitor.ratio", "0.5").toDouble).getOrElse(0.5)
  private[this] val queue = new LinkedBlockingQueue[(FiniteDuration, Long)]
  private[this] val lastWarned = new AtomicReference(Deadline(0.millis))
  private[this] def handleNotification(
      notification: Notification,
      bean: GarbageCollectorMXBean,
      totalCollectionTime: AtomicLong
  ): Unit = {
    val now = notification.getTimeStamp.millis
    val collectionTime = bean.getCollectionTime
    val elapsed = collectionTime - totalCollectionTime.getAndSet(collectionTime)
    queue.removeIf { case (whenEventHappened, _) => now > whenEventHappened + window }
    queue.add(now -> elapsed)
    val total = queue.asScala.foldLeft(0L) { case (total, (_, t)) => total + t }
    if ((total > window.toMillis * ratio) && (lastWarned.get + window).isOverdue) {
      lastWarned.set(Deadline.now)
      val msg = s"${total / 1000.0} seconds of the last $window were spent in garbage " +
        "collection. You may want to increase the project heap size using `-Xmx` or try " +
        "a different gc algorithm, e.g. `-XX:+UseG1GC`, for better performance."
      logger.warn(msg)
    }
  }

  val removers = ManagementFactory.getGarbageCollectorMXBeans.asScala.flatMap {
    case bean: NotificationEmitter =>
      val elapsedTime = new AtomicLong(bean.getCollectionTime)
      val listener: NotificationListener =
        (notification, _) => handleNotification(notification, bean, elapsedTime)
      bean.addNotificationListener(listener, null, null)
      Some(() => bean.removeNotificationListener(listener))
    case _ => None
  }
  override def close(): Unit = {
    removers.foreach(_.apply())
  }
}
