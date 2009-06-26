/* sbt -- Simple Build Tool
 * Copyright 2009  Mikko Peltonen, Mark Harrah
 */
package sbt

object SourceModificationWatch
{
	def watchUntil(project: Project, pollDelaySec: Int)(terminationCondition: => Boolean)(onSourcesModified: => Unit)
	{
		def sourceFiles: Iterable[java.io.File] =
			sourcesFinder.get.map(Path.relativize(project.info.projectPath, _)).filter(_.isDefined).map(_.get.asFile)
		def sourcesFinder: PathFinder = (Path.emptyPathFinder /: project.topologicalSort)(_ +++ _.watchPaths)
		def loop(lastCallbackCallTime: Long, previousFileCount: Int)
		{
			val (lastModifiedTime, fileCount) = sourceFiles.foldLeft((0L, 0)){(acc, file) => (Math.max(acc._1, file.lastModified), acc._2 + 1)}
			val newCallbackCallTime =
				// check if sources are modified
				if (lastModifiedTime > lastCallbackCallTime || previousFileCount != fileCount)
				{
					val now = System.currentTimeMillis
					onSourcesModified
					now
				}
				else
					lastCallbackCallTime
			Thread.sleep(pollDelaySec * 1000)
			if(!terminationCondition)
				loop(newCallbackCallTime, fileCount)
		}
		loop(0L, 0)
	}
}