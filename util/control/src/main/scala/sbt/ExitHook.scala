/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

/** Defines a function to call as sbt exits.*/
trait ExitHook
{
	/** Subclasses should implement this method, which is called when this hook is executed. */
	def runBeforeExiting(): Unit
}
object ExitHook
{
	def apply(f: => Unit): ExitHook = new ExitHook { def runBeforeExiting() = f }
}

object ExitHooks
{
	/** Calls each registered exit hook, trapping any exceptions so that each hook is given a chance to run. */
	def runExitHooks(exitHooks: Seq[ExitHook]): Seq[Throwable] =
		exitHooks.flatMap( hook =>
			ErrorHandling.wideConvert( hook.runBeforeExiting() ).left.toOption
		)
}