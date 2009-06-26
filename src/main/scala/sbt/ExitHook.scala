/* sbt -- Simple Build Tool
 * Copyright 2009  Mark Harrah
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
	/** This is a list of hooks to call when sbt is finished executing.*/
	private val exitHooks = new scala.collection.mutable.HashSet[ExitHook]
	/** Adds a hook to call before sbt exits. */
	private[sbt] def register(hook: ExitHook) { exitHooks += hook }
	/** Removes a hook. */
	private[sbt] def unregister(hook: ExitHook) { exitHooks -= hook }
	/** Calls each registered exit hook, trapping any exceptions so that each hook is given a chance to run. */
	private[sbt] def runExitHooks(log: Logger)
	{
		for(hook <- exitHooks.toList)
		{
			try
			{
				log.debug("Running exit hook '" + hook.name + "'...")
				hook.runBeforeExiting()
			}
			catch
			{
				case e =>
				{
					log.trace(e);
					log.error("Error running exit hook '" + hook.name + "': " + e.toString)
				}
			}
		}
	}
}