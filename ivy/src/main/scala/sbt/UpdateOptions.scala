package sbt

import java.io.File

/**
 * Represents configurable options for update task.
 * While UpdateConfiguration is passed into update at runtime,
 * UpdateOption is intended to be used while setting up the Ivy object.
 *
 * See also UpdateConfiguration in IvyActions.scala.
 */
final class UpdateOptions(
    /** If set to true, use consolidated resolution. */
    val consolidatedResolution: Boolean) {

  def withConsolidatedResolution(consolidatedResolution: Boolean): UpdateOptions =
    copy(consolidatedResolution = consolidatedResolution)

  private[sbt] def copy(
    consolidatedResolution: Boolean = this.consolidatedResolution): UpdateOptions =
    new UpdateOptions(consolidatedResolution)
}

object UpdateOptions {
  def apply(): UpdateOptions =
    new UpdateOptions(false)
}
