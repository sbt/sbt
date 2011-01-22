/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mikko Peltonen, Stuart Roebuck, Mark Harrah
 */
package sbt

	import CommandSupport.FailureWall
	import annotation.tailrec

trait Watched
{
	/** A `PathFinder` that determines the files watched when an action is run with a preceeding ~ when this is the current
	* project.  This project does not need to include the watched paths for projects that this project depends on.*/
	def watchPaths: PathFinder = Path.emptyPathFinder
	def terminateWatch(key: Int): Boolean = Watched.isEnter(key)
}

object Watched
{
	private[this] class AWatched extends Watched
	
	def multi(base: Watched, paths: Seq[Watched]): Watched = 
		new AWatched
		{
			override val watchPaths = (base.watchPaths /: paths)(_ +++ _.watchPaths)
			override def terminateWatch(key: Int): Boolean = base.terminateWatch(key)
		}
	def empty: Watched = new AWatched
		
	val PollDelaySeconds = 1
	def isEnter(key: Int): Boolean = key == 10 || key == 13

	def executeContinuously(watched: Watched, s: State, next: String, repeat: String): State =
	{
		@tailrec def shouldTerminate: Boolean = (System.in.available > 0) && (watched.terminateWatch(System.in.read()) || shouldTerminate)
		val sourcesFinder = watched.watchPaths
		val watchState = s get ContinuousState getOrElse WatchState.empty

		if(watchState.count > 0)
			System.out.println(watchState.count + ". Waiting for source changes... (press enter to interrupt)")

		val (triggered, newWatchState) = SourceModificationWatch.watch(sourcesFinder, PollDelaySeconds, watchState)(shouldTerminate)

		if(triggered)
			(next :: FailureWall :: repeat :: s).put(ContinuousState, newWatchState)
		else
		{
			while (System.in.available() > 0) System.in.read()
			s.put(ContinuousState, WatchState.empty)
		}
	}
	val ContinuousState = AttributeKey[WatchState]("watch state")
}