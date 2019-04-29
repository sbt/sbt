/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

sealed trait IfMissing

object IfMissing {
  def fail: IfMissing = Fail

  /** f is expected to call ZincComponentManager.define.  */
  def define(useSecondaryCache: Boolean, f: => Unit): IfMissing = new Define(useSecondaryCache, f)
  object Fail extends IfMissing
  final class Define(val useSecondaryCache: Boolean, define: => Unit) extends IfMissing {
    def run(): Unit = define
  }
}
