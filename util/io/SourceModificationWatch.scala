/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mikko Peltonen, Stuart Roebuck, Mark Harrah
 */
package sbt

object SourceModificationWatch
{
	def watchUntil(sourcesFinder: PathFinder, pollDelaySec: Int)(terminationCondition: => Boolean)(onSourcesModified: => Unit)
	{
		def sourceFiles: Iterable[java.io.File] = sourcesFinder.getFiles
		def loop(lastCallbackCallTime: Long, previousFileCount: Int, awaitingQuietPeriod:Boolean)
		{
			val (lastModifiedTime, fileCount) =
				( (0L, 0) /: sourceFiles) {(acc, file) => (math.max(acc._1, file.lastModified), acc._2 + 1)}

			val sourcesModified =
				lastModifiedTime > lastCallbackCallTime ||
				previousFileCount != fileCount

			val newCallbackCallTime =
				if (sourcesModified && !awaitingQuietPeriod)
					System.currentTimeMillis
				else if (!sourcesModified && awaitingQuietPeriod)
				{
					onSourcesModified
					lastCallbackCallTime
				}
				else
					lastCallbackCallTime

			Thread.sleep(pollDelaySec * 1000)
			if(!terminationCondition)
				loop(newCallbackCallTime, fileCount, sourcesModified)
		}
		loop(0L, 0, false)
	}
}