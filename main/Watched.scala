/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mikko Peltonen, Stuart Roebuck, Mark Harrah
 */
package sbt

	import CommandSupport.{ClearOnFailure,FailureWall}
	import annotation.tailrec
	import java.io.File

trait Watched
{
	/** The files watched when an action is run with a preceeding ~ */
	def watchPaths(s: State): Seq[File] = Nil
	def terminateWatch(key: Int): Boolean = Watched.isEnter(key)
	/** The time in milliseconds between checking for changes.  The actual time between the last change made to a file and the
	* execution time is between `pollInterval` and `pollInterval*2`.*/
	def pollInterval: Int = Watched.PollDelayMillis
}

object Watched
{
	private[this] class AWatched extends Watched
	
	def multi(base: Watched, paths: Seq[Watched]): Watched = 
		new AWatched
		{
			override def watchPaths(s: State) = (base.watchPaths(s) /: paths)(_ ++ _.watchPaths(s))
			override def terminateWatch(key: Int): Boolean = base.terminateWatch(key)
			override val pollInterval = (base +: paths).map(_.pollInterval).min
		}
	def empty: Watched = new AWatched
		
	val PollDelayMillis = 500
	def isEnter(key: Int): Boolean = key == 10 || key == 13

	def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State =
	{
		@tailrec def shouldTerminate: Boolean = (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
		val sourcesFinder = Path.finder { watched watchPaths s }
		val watchState = s get ContinuousState getOrElse WatchState.empty

		if(watchState.count > 0)
			System.out.println(watchState.count + ". Waiting for source changes... (press enter to interrupt)")

		val (triggered, newWatchState, newState) =
			try {
				val (triggered, newWatchState) = SourceModificationWatch.watch(sourcesFinder, watched.pollInterval, watchState)(shouldTerminate)
				(triggered, newWatchState, s)
			}
			catch { case e: Exception =>
				val log = CommandSupport.logger(s)
				log.error("Error occurred obtaining files to watch.  Terminating continuous execution...")
				BuiltinCommands.handleException(e, s, log)
				(false, watchState, s.fail)
			}

		if(triggered)
			(ClearOnFailure :: next :: FailureWall :: repeat :: s).put(ContinuousState, newWatchState)
		else
		{
			while (System.in.available() > 0) System.in.read()
			s.put(ContinuousState, WatchState.empty)
		}
	}
	val ContinuousState = AttributeKey[WatchState]("watch state", "Internal: tracks state for continuous execution.")
	val Configuration = AttributeKey[Watched]("watched-configuration", "Configures continuous execution.")
}