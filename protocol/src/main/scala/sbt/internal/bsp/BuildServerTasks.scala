/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.bsp

import java.util.concurrent.atomic.AtomicInteger

object BuildServerTasks {
  private val idGenerator = new AtomicInteger(0)
  def uniqueId: String = idGenerator.getAndIncrement().toString
}
