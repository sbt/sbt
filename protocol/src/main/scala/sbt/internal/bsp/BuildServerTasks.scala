package sbt.internal.bsp

import java.util.concurrent.atomic.AtomicInteger

object BuildServerTasks {
  private val idGenerator = new AtomicInteger(0)
  def uniqueId: String = idGenerator.getAndIncrement().toString
}
