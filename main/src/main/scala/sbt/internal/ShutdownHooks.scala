/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.{ AtomicBoolean, AtomicInteger }

import scala.util.control.NonFatal

private[sbt] object ShutdownHooks extends AutoCloseable {
  private val idGenerator = new AtomicInteger(0)
  private val hooks = new ConcurrentHashMap[Int, () => Unit]
  private val ranHooks = new AtomicBoolean(false)
  private val thread = new Thread("shutdown-hooks-run-all") {
    override def run(): Unit = runAll()
  }
  private val runtime = Runtime.getRuntime
  runtime.addShutdownHook(thread)
  private[sbt] def add[R](task: () => R): AutoCloseable = {
    val id = idGenerator.getAndIncrement()
    hooks.put(
      id,
      () =>
        try {
          task()
          ()
        } catch {
          case NonFatal(e) =>
            System.err.println(s"Caught exception running shutdown hook: $e")
            e.printStackTrace(System.err)
        }
    )
    () => Option(hooks.remove(id)).foreach(_.apply())
  }
  private def runAll(): Unit = if (ranHooks.compareAndSet(false, true)) {
    hooks.forEachValue(runtime.availableProcessors.toLong, (_: () => Unit).apply())
  }
  override def close(): Unit = {
    runtime.removeShutdownHook(thread)
    runAll()
  }
}
