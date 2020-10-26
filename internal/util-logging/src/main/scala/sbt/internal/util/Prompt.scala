/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

import java.util.concurrent.{ CountDownLatch, LinkedBlockingDeque, LinkedBlockingQueue }
import java.util.concurrent.atomic.AtomicReference
import scala.collection.JavaConverters._

private[sbt] sealed trait Prompt {
  def mkPrompt: () => String
  def render(): String
  def reset(): Unit
}

private[sbt] object Prompt {
  private[sbt] case class AskUser(override val mkPrompt: () => String) extends Prompt {
    private[this] val bytes = new LinkedBlockingQueue[Byte]
    def write(b: Array[Byte]): Unit = b.foreach(bytes.put)
    override def render(): String = {
      val res = new String(bytes.asScala.toArray, "UTF-8")
      if (res.endsWith(System.lineSeparator)) "" else res
    }
    override def reset(): Unit = bytes.clear()
  }
  private[sbt] trait NoPrompt extends Prompt {
    override val mkPrompt: () => String = () => ""
    override def render(): String = ""
    override def reset(): Unit = {}
  }
  private[sbt] case object Running extends NoPrompt
  private[sbt] case object Batch extends NoPrompt
  private[sbt] case object Watch extends NoPrompt
  private[sbt] case object Pending extends NoPrompt
  private[sbt] case object NoPrompt extends NoPrompt
  private[sbt] final class Blocked(doJoin: () => Boolean) extends NoPrompt {
    private[this] val joinHandle = new AtomicReference(doJoin)
    private[this] val execIDHolder = new AtomicReference[String]
    private[this] val threadHolder = new AtomicReference[Thread]
    private[sbt] def setExecID(id: String): Unit = execIDHolder.set(id)
    private[sbt] def execID: Option[String] = Option(execIDHolder.get)
    private[sbt] def setMainThread(thread: Thread) = threadHolder.set(thread)
    private[sbt] def cancel(): Unit = Option(threadHolder.get).foreach(_.interrupt())
    private[this] val finishLatch = new CountDownLatch(1)

    /**
     * There may be some cleanup that we want to do after the blocked task completes.
     * In particular, the logger context normally clears appenders after task evaluation
     * completes, but we don't want to do that for a blocked task. We do want to do
     * it after the blocked task completes.
     */
    private[this] val callbacks = new LinkedBlockingDeque[() => Unit]

    /**
     * These are the pending commands for the blocked terminal that are intended
     * to run after the task actually completes.
     */
    private[this] val pendingExecs = new LinkedBlockingDeque[(String, Option[String])]

    /**
     * The completion logger is used to defer the timing results from Aggregation
     * util the task completes.
     */
    private[this] val completionLogger = new AtomicReference[(Long, Boolean) => Unit]
    private[sbt] def addCallback(cb: () => Unit): Unit = Util.ignoreResult(callbacks.add(cb))
    private[sbt] def appendExec(exec: (String, Option[String])): Unit = {
      Util.ignoreResult(pendingExecs.addLast(exec))
    }
    private[sbt] def prependExec(exec: (String, Option[String])): Unit = {
      Util.ignoreResult(pendingExecs.addFirst(exec))
    }
    private[sbt] def getExecs: Seq[(String, Option[String])] = pendingExecs.asScala.toVector

    private[sbt] def addCompletionLogger(logger: (Long, Boolean) => Unit) =
      completionLogger.set(logger)
    private[sbt] def join(): Unit = {
      val doJoin = joinHandle.getAndSet(null)
      if (doJoin != null) {
        try {
          val res = doJoin()
          Option(completionLogger.get).foreach(_.apply(System.currentTimeMillis, res))
        } finally {
          callbacks.synchronized {
            callbacks.forEach(_.apply())
            callbacks.clear()
          }
          finishLatch.countDown()
        }
      } else finishLatch.await()
    }
  }
}
