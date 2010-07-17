/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mark Harrah
 */
package sbt

/** Defines a function to call as sbt exits.*/
trait ExitHook extends NotNull
{
	/** Provides a name for this hook to be used to provide feedback to the user. */
	def name: String
	/** Subclasses should implement this method, which is called when this hook is executed. */
	def runBeforeExiting(): Unit
}

object ExitHooks
{
	/** Calls each registered exit hook, trapping any exceptions so that each hook is given a chance to run. */
	def runExitHooks(exitHooks: Seq[ExitHook]): Seq[Throwable] =
		exitHooks.flatMap( hook =>
			ErrorHandling.wideConvert( hook.runBeforeExiting() ).left.toOption
		)
}