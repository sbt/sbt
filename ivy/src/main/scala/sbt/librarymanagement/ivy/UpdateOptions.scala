package sbt.librarymanagement
package ivy

import org.apache.ivy.plugins.resolver.DependencyResolver
import org.apache.ivy.core.settings.IvySettings
import sbt.util.Logger
import sbt.internal.librarymanagement.LMSysProp

/**
 * Represents configurable options for update task.
 * While UpdateConfiguration is passed into update at runtime,
 * UpdateOption is intended to be used while setting up the Ivy object.
 *
 * See also UpdateConfiguration in IvyActions.scala.
 */
final class UpdateOptions private[sbt] (
    // If set to CircularDependencyLevel.Error, halt the dependency resolution.
    val circularDependencyLevel: CircularDependencyLevel,
    // If set to true, prioritize inter-project resolver
    val interProjectFirst: Boolean,
    // If set to true, check all resolvers for snapshots.
    val latestSnapshots: Boolean,
    // If set to true, use cached resolution.
    val cachedResolution: Boolean,
    // If set to true, use Gigahorse
    val gigahorse: Boolean,
    // Extension point for an alternative resolver converter.
    val resolverConverter: UpdateOptions.ResolverConverter,
    // Map the unique resolver to be checked for the module ID
    val moduleResolvers: Map[ModuleID, Resolver]
) {
  def withCircularDependencyLevel(
      circularDependencyLevel: CircularDependencyLevel
  ): UpdateOptions =
    copy(circularDependencyLevel = circularDependencyLevel)
  def withInterProjectFirst(interProjectFirst: Boolean): UpdateOptions =
    copy(interProjectFirst = interProjectFirst)
  def withLatestSnapshots(latestSnapshots: Boolean): UpdateOptions =
    copy(latestSnapshots = latestSnapshots)
  def withCachedResolution(cachedResolution: Boolean): UpdateOptions =
    copy(cachedResolution = cachedResolution)

  def withGigahorse(gigahorse: Boolean): UpdateOptions =
    copy(gigahorse = gigahorse)

  /** Extention point for an alternative resolver converter. */
  def withResolverConverter(resolverConverter: UpdateOptions.ResolverConverter): UpdateOptions =
    copy(resolverConverter = resolverConverter)

  def withModuleResolvers(moduleResolvers: Map[ModuleID, Resolver]): UpdateOptions =
    copy(moduleResolvers = moduleResolvers)

  private[sbt] def copy(
      circularDependencyLevel: CircularDependencyLevel = this.circularDependencyLevel,
      interProjectFirst: Boolean = this.interProjectFirst,
      latestSnapshots: Boolean = this.latestSnapshots,
      cachedResolution: Boolean = this.cachedResolution,
      gigahorse: Boolean = this.gigahorse,
      resolverConverter: UpdateOptions.ResolverConverter = this.resolverConverter,
      moduleResolvers: Map[ModuleID, Resolver] = this.moduleResolvers
  ): UpdateOptions =
    new UpdateOptions(
      circularDependencyLevel,
      interProjectFirst,
      latestSnapshots,
      cachedResolution,
      gigahorse,
      resolverConverter,
      moduleResolvers
    )

  override def toString(): String =
    s"""UpdateOptions(
        |  circularDependencyLevel = $circularDependencyLevel,
        |  latestSnapshots = $latestSnapshots,
        |  cachedResolution = $cachedResolution
        |)""".stripMargin

  override def equals(o: Any): Boolean = o match {
    case o: UpdateOptions =>
      this.circularDependencyLevel == o.circularDependencyLevel &&
      this.interProjectFirst == o.interProjectFirst &&
      this.latestSnapshots == o.latestSnapshots &&
      this.cachedResolution == o.cachedResolution &&
      this.gigahorse == o.gigahorse &&
      this.resolverConverter == o.resolverConverter &&
      this.moduleResolvers == o.moduleResolvers
    case _ => false
  }

  override def hashCode: Int = {
    var hash = 1
    hash = hash * 31 + this.circularDependencyLevel.##
    hash = hash * 31 + this.interProjectFirst.##
    hash = hash * 31 + this.latestSnapshots.##
    hash = hash * 31 + this.cachedResolution.##
    hash = hash * 31 + this.gigahorse.##
    hash = hash * 31 + this.resolverConverter.##
    hash = hash * 31 + this.moduleResolvers.##
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
      cachedResolution = false,
      gigahorse = LMSysProp.useGigahorse,
      resolverConverter = PartialFunction.empty,
      moduleResolvers = Map.empty
    )
}
