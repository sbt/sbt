/* sbt -- Simple Build Tool
 * Copyright 2009, 2010  Mikko Peltonen, Stuart Roebuck, Mark Harrah
 */
package sbt

import annotation.tailrec

object SourceModificationWatch {
  @tailrec def watch(sourcesFinder: PathFinder, pollDelayMillis: Int, state: WatchState)(terminationCondition: => Boolean): (Boolean, WatchState) =
    {
      if (pollDelayMillis < 1000) {
        throw new IllegalArgumentException(
            "pollDelayMillis must be at least 1000 since many filesystems only support second-level granularity for last-modification time")
      }

      import state._

      val sourceFiles: Iterable[java.io.File] = sourcesFinder.get
      val sourceFilesPath: Set[String] = sourceFiles.map(_.getCanonicalPath)(collection.breakOut)
      val lastModifiedTime =
        (0L /: sourceFiles) { (acc, file) => math.max(acc, file.lastModified) }

      val sourcesModified =
        lastModifiedTime > lastCallbackCallTime ||
          previousFiles != sourceFilesPath

      val (triggered, newCallbackCallTime) =
        if (sourcesModified)
          (false, System.currentTimeMillis)
        else
          (awaitingQuietPeriod, lastCallbackCallTime)

      val newState = new WatchState(newCallbackCallTime, sourceFilesPath, sourcesModified, if (triggered) count + 1 else count)
      if (triggered)
        (true, newState)
      else {
        Thread.sleep(pollDelayMillis)
        if (terminationCondition)
          (false, newState)
        else
          watch(sourcesFinder, pollDelayMillis, newState)(terminationCondition)
      }
    }
}
final class WatchState(val lastCallbackCallTime: Long, val previousFiles: Set[String], val awaitingQuietPeriod: Boolean, val count: Int) {

  def previousFileCount: Int = previousFiles.size

  @deprecated("Use another constructor", "0.13.6")
  def this(lastCallbackCallTime: Long, previousFileCount: Int, awaitingQuietPeriod: Boolean, count: Int) {
    this(lastCallbackCallTime, Set.empty[String], awaitingQuietPeriod, count)
  }
}

object WatchState {
  def empty = new WatchState(0L, Set.empty[String], false, 0)
}
