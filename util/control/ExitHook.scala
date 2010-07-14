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

trait ExitHookRegistry
{
	def register(hook: ExitHook): Unit
	def unregister(hook: ExitHook): Unit
}


class ExitHooks extends ExitHookRegistry
{
	private val exitHooks = new scala.collection.mutable.HashSet[ExitHook]
	def register(hook: ExitHook) { exitHooks += hook }
	def unregister(hook: ExitHook) { exitHooks -= hook }
	/** Calls each registered exit hook, trapping any exceptions so that each hook is given a chance to run. */
	def runExitHooks(debug: String => Unit): List[Throwable] =
		exitHooks.toList.flatMap( hook =>
			ErrorHandling.wideConvert( hook.runBeforeExiting() ).left.toOption
		)
}