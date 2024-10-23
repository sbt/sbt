/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

/**
 * An enumeration defining the levels available for logging. A level includes all of the levels with
 * id larger than its own id. For example, Warn (id=3) includes Error (id=4).
 */
enum Level(val id: Int, private val name: String) extends Ordered[Level] {
  case Debug extends Level(1, "debug")
  case Info extends Level(2, "info")
  case Warn extends Level(3, "warn")
  case Error extends Level(4, "error")

  final override def compare(that: Level): Int = {
    if (this.id < that.id) -1
    else if (this.id == that.id) 0
    else 1
  }
}

object Level {
  type Value = Level

  /**
   * Defines the label to use for success messages. Because the label for levels is defined in this
   * module, the success label is also defined here.
   */
  val SuccessLabel = "success"

  def union(a: Level, b: Level): Level = if (a.id < b.id) a else b
  def unionAll(vs: Seq[Level]): Level = vs reduceLeft union

  /**
   * Returns the level with the given name wrapped in Some, or None if no level exists for that
   * name.
   */
  def apply(s: String): Option[Level] = Level.values.find(s == _.name)

  /** Same as apply, defined for use in pattern matching. */
  private[sbt] def unapply(s: String): Option[Level] = apply(s)
}
