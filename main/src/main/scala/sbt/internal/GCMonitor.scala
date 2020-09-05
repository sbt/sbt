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
  private[this] def handleNotification(
      notification: Notification,
      bean: GarbageCollectorMXBean,
      info: (LinkedBlockingQueue[(Deadline, Long)], AtomicLong, AtomicReference[Deadline])
  ): Unit = {
    val (queue, lastCollectionTime, lastWarned) = info
    val now = Deadline.now
    val collectionTime = bean.getCollectionTime
    val elapsed = collectionTime - lastCollectionTime.getAndSet(collectionTime)
    queue.removeIf { case (d, _) => (d + window).isOverdue }
    queue.add(now -> elapsed)
    val total = queue.asScala.foldLeft(0L) { case (total, (_, t)) => total + t }
    if ((total > window.toMillis * ratio) && (lastWarned.get + window).isOverdue) {
      lastWarned.set(now)
      val msg = s"${total / 1000.0} seconds of the last $window were spent in garbage " +
        "collection. You may want to increase the project heap size for better performance."
      logger.warn(msg)
    }
  }

  val removers = ManagementFactory.getGarbageCollectorMXBeans.asScala.flatMap {
    case e: NotificationEmitter =>
      val queue = new LinkedBlockingQueue[(Deadline, Long)]
      val lastCollectionTime = new AtomicLong(0L)
      val lastLogged = new AtomicReference(Deadline(0.millis))
      val listener: NotificationListener =
        (notification, queue) =>
          queue match {
            case (
                q: LinkedBlockingQueue[(Deadline, Long)] @unchecked,
                lct: AtomicLong,
                ll: AtomicReference[Deadline] @unchecked
                ) =>
              handleNotification(notification, e, (q, lct, ll))
            case _ =>
          }
      e.addNotificationListener(listener, null, (queue, lastCollectionTime, lastLogged))
      Some(() => e.removeNotificationListener(listener))
    case _ => None
  }
  override def close(): Unit = {
    removers.foreach(_.apply())
  }
}
