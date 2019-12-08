/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.util

/** Defines a function to call as sbt exits.*/
trait ExitHook {

  /** Subclasses should implement this method, which is called when this hook is executed. */
  def runBeforeExiting(): Unit

}

object ExitHook {
  def apply(f: => Unit): ExitHook = new ExitHook { def runBeforeExiting() = f }
}

object ExitHooks {

  /** Calls each registered exit hook, trapping any exceptions so that each hook is given a chance to run. */
  def runExitHooks(exitHooks: Seq[ExitHook]): Seq[Throwable] =
    exitHooks.flatMap(hook => ErrorHandling.wideConvert(hook.runBeforeExiting()).left.toOption)

}
