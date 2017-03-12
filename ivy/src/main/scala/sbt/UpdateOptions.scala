package sbt

import java.io.File
import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.core.settings.IvySettings

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
                                        // If set to true, prioritize inter-project resolver
                                        val interProjectFirst: Boolean,
                                        /** If set to true, check all resolvers for snapshots. */
                                        val latestSnapshots: Boolean,
                                        /** If set to true, use consolidated resolution. */
                                        val consolidatedResolution: Boolean,
                                        /** If set to true, use cached resolution. */
                                        val cachedResolution: Boolean,
                                        /** Extention point for an alternative resolver converter. */
                                        val resolverConverter: UpdateOptions.ResolverConverter) {
  def withCircularDependencyLevel(circularDependencyLevel: CircularDependencyLevel): UpdateOptions =
    copy(circularDependencyLevel = circularDependencyLevel)
  def withInterProjectFirst(interProjectFirst: Boolean): UpdateOptions =
    copy(interProjectFirst = interProjectFirst)
  def withLatestSnapshots(latestSnapshots: Boolean): UpdateOptions =
    copy(latestSnapshots = latestSnapshots)
  @deprecated("Use withCachedResolution instead.", "0.13.7")
  def withConsolidatedResolution(consolidatedResolution: Boolean): UpdateOptions =
    copy(consolidatedResolution = consolidatedResolution, cachedResolution = consolidatedResolution)
  def withCachedResolution(cachedResoluton: Boolean): UpdateOptions =
    copy(cachedResolution = cachedResoluton, consolidatedResolution = cachedResolution)

  /** Extention point for an alternative resolver converter. */
  def withResolverConverter(resolverConverter: UpdateOptions.ResolverConverter): UpdateOptions =
    copy(resolverConverter = resolverConverter)

  private[sbt] def copy(
      circularDependencyLevel: CircularDependencyLevel = this.circularDependencyLevel,
      interProjectFirst: Boolean = this.interProjectFirst,
      latestSnapshots: Boolean = this.latestSnapshots,
      consolidatedResolution: Boolean = this.consolidatedResolution,
      cachedResolution: Boolean = this.cachedResolution,
      resolverConverter: UpdateOptions.ResolverConverter = this.resolverConverter
  ): UpdateOptions =
    new UpdateOptions(
      circularDependencyLevel,
      interProjectFirst,
      latestSnapshots,
      consolidatedResolution,
      cachedResolution,
      resolverConverter
    )

  override def equals(o: Any): Boolean = o match {
    case o: UpdateOptions =>
      this.circularDependencyLevel == o.circularDependencyLevel &&
        this.interProjectFirst == o.interProjectFirst &&
        this.latestSnapshots == o.latestSnapshots &&
        this.cachedResolution == o.cachedResolution &&
        this.resolverConverter == o.resolverConverter
    case _ => false
  }

  override def hashCode: Int = {
    var hash = 1
    hash = hash * 31 + this.circularDependencyLevel.##
    hash = hash * 31 + this.interProjectFirst.##
    hash = hash * 31 + this.latestSnapshots.##
    hash = hash * 31 + this.cachedResolution.##
    hash = hash * 31 + this.resolverConverter.##
    hash
  }
}

object UpdateOptions {
  type ResolverConverter = PartialFunction[(Resolver, IvySettings, Logger), DependencyResolver]

  def apply(): UpdateOptions =
    new UpdateOptions(
      circularDependencyLevel = CircularDependencyLevel.Warn,
      interProjectFirst = true,
      latestSnapshots = true,
      consolidatedResolution = false,
      cachedResolution = false,
      resolverConverter = PartialFunction.empty
    )
}
