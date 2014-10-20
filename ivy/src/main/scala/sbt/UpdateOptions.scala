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
    val consolidatedResolution: Boolean,
    /** If set to true, use cached resolution. */
    val cachedResolution: Boolean) {

  def withCircularDependencyLevel(circularDependencyLevel: CircularDependencyLevel): UpdateOptions =
    copy(circularDependencyLevel = circularDependencyLevel)
  def withLatestSnapshots(latestSnapshots: Boolean): UpdateOptions =
    copy(latestSnapshots = latestSnapshots)
  @deprecated("Use withCachedResolution instead.", "0.13.7")
  def withConsolidatedResolution(consolidatedResolution: Boolean): UpdateOptions =
    copy(consolidatedResolution = consolidatedResolution,
      cachedResolution = consolidatedResolution)
  def withCachedResolution(cachedResoluton: Boolean): UpdateOptions =
    copy(cachedResolution = cachedResoluton,
      consolidatedResolution = cachedResolution)

  private[sbt] def copy(
    circularDependencyLevel: CircularDependencyLevel = this.circularDependencyLevel,
    latestSnapshots: Boolean = this.latestSnapshots,
    consolidatedResolution: Boolean = this.consolidatedResolution,
    cachedResolution: Boolean = this.cachedResolution): UpdateOptions =
    new UpdateOptions(circularDependencyLevel,
      latestSnapshots,
      consolidatedResolution,
      cachedResolution)
}

object UpdateOptions {
  def apply(): UpdateOptions =
    new UpdateOptions(
      circularDependencyLevel = CircularDependencyLevel.Warn,
      latestSnapshots = false,
      consolidatedResolution = false,
      cachedResolution = false)
}
