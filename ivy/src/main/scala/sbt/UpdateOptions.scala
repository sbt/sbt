package sbt

import java.io.File

/**
 * Represents configurable options for update task.
 * While UpdateConfiguration is passed into update at runtime,
 * UpdateOption is intended to be used while setting up the Ivy object.
 *
 * See also UpdateConfiguration in IvyActions.scala.
 */
final class UpdateOptions private[sbt] (
    /** If set to CircularDependencyLevel.Error, halt the dependency resolution. */
    val circularDependencyLevel: CircularDependencyLevel,
    /** If set to true, check all resolvers for snapshots. */
    val latestSnapshots: Boolean,
    /** If set to true, use consolidated resolution. */
    val consolidatedResolution: Boolean) {

  def withCircularDependencyLevel(circularDependencyLevel: CircularDependencyLevel): UpdateOptions =
    copy(circularDependencyLevel = circularDependencyLevel)
  def withLatestSnapshots(latestSnapshots: Boolean): UpdateOptions =
    copy(latestSnapshots = latestSnapshots)
  def withConsolidatedResolution(consolidatedResolution: Boolean): UpdateOptions =
    copy(consolidatedResolution = consolidatedResolution)

  private[sbt] def copy(
    circularDependencyLevel: CircularDependencyLevel = this.circularDependencyLevel,
    latestSnapshots: Boolean = this.latestSnapshots,
    consolidatedResolution: Boolean = this.consolidatedResolution): UpdateOptions =
    new UpdateOptions(circularDependencyLevel,
      latestSnapshots,
      consolidatedResolution)
}

object UpdateOptions {
  def apply(): UpdateOptions =
    new UpdateOptions(
      circularDependencyLevel = CircularDependencyLevel.Warn,
      latestSnapshots = true,
      consolidatedResolution = false)
}
