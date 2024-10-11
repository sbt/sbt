/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
final class InlineIvyConfiguration private (
  lock: Option[xsbti.GlobalLock],
  log: Option[xsbti.Logger],
  updateOptions: sbt.librarymanagement.ivy.UpdateOptions,
  val paths: Option[sbt.librarymanagement.ivy.IvyPaths],
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val otherResolvers: Vector[sbt.librarymanagement.Resolver],
  val moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration],
  val checksums: Vector[String],
  val managedChecksums: Boolean,
  val resolutionCacheDir: Option[java.io.File]) extends sbt.librarymanagement.ivy.IvyConfiguration(lock, log, updateOptions) with Serializable {
  
  private def this() = this(None, None, sbt.librarymanagement.ivy.UpdateOptions(), None, sbt.librarymanagement.Resolver.defaults, Vector.empty, Vector.empty, sbt.librarymanagement.ivy.IvyDefaults.defaultChecksums, false, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: InlineIvyConfiguration => (this.lock == x.lock) && (this.log == x.log) && (this.updateOptions == x.updateOptions) && (this.paths == x.paths) && (this.resolvers == x.resolvers) && (this.otherResolvers == x.otherResolvers) && (this.moduleConfigurations == x.moduleConfigurations) && (this.checksums == x.checksums) && (this.managedChecksums == x.managedChecksums) && (this.resolutionCacheDir == x.resolutionCacheDir)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ivy.InlineIvyConfiguration".##) + lock.##) + log.##) + updateOptions.##) + paths.##) + resolvers.##) + otherResolvers.##) + moduleConfigurations.##) + checksums.##) + managedChecksums.##) + resolutionCacheDir.##)
  }
  override def toString: String = {
    "InlineIvyConfiguration(" + lock + ", " + log + ", " + updateOptions + ", " + paths + ", " + resolvers + ", " + otherResolvers + ", " + moduleConfigurations + ", " + checksums + ", " + managedChecksums + ", " + resolutionCacheDir + ")"
  }
  private[this] def copy(lock: Option[xsbti.GlobalLock] = lock, log: Option[xsbti.Logger] = log, updateOptions: sbt.librarymanagement.ivy.UpdateOptions = updateOptions, paths: Option[sbt.librarymanagement.ivy.IvyPaths] = paths, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, otherResolvers: Vector[sbt.librarymanagement.Resolver] = otherResolvers, moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration] = moduleConfigurations, checksums: Vector[String] = checksums, managedChecksums: Boolean = managedChecksums, resolutionCacheDir: Option[java.io.File] = resolutionCacheDir): InlineIvyConfiguration = {
    new InlineIvyConfiguration(lock, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
  }
  def withLock(lock: Option[xsbti.GlobalLock]): InlineIvyConfiguration = {
    copy(lock = lock)
  }
  def withLock(lock: xsbti.GlobalLock): InlineIvyConfiguration = {
    copy(lock = Option(lock))
  }
  def withLog(log: Option[xsbti.Logger]): InlineIvyConfiguration = {
    copy(log = log)
  }
  def withLog(log: xsbti.Logger): InlineIvyConfiguration = {
    copy(log = Option(log))
  }
  def withUpdateOptions(updateOptions: sbt.librarymanagement.ivy.UpdateOptions): InlineIvyConfiguration = {
    copy(updateOptions = updateOptions)
  }
  def withPaths(paths: Option[sbt.librarymanagement.ivy.IvyPaths]): InlineIvyConfiguration = {
    copy(paths = paths)
  }
  def withPaths(paths: sbt.librarymanagement.ivy.IvyPaths): InlineIvyConfiguration = {
    copy(paths = Option(paths))
  }
  def withResolvers(resolvers: Vector[sbt.librarymanagement.Resolver]): InlineIvyConfiguration = {
    copy(resolvers = resolvers)
  }
  def withOtherResolvers(otherResolvers: Vector[sbt.librarymanagement.Resolver]): InlineIvyConfiguration = {
    copy(otherResolvers = otherResolvers)
  }
  def withModuleConfigurations(moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration]): InlineIvyConfiguration = {
    copy(moduleConfigurations = moduleConfigurations)
  }
  def withChecksums(checksums: Vector[String]): InlineIvyConfiguration = {
    copy(checksums = checksums)
  }
  def withManagedChecksums(managedChecksums: Boolean): InlineIvyConfiguration = {
    copy(managedChecksums = managedChecksums)
  }
  def withResolutionCacheDir(resolutionCacheDir: Option[java.io.File]): InlineIvyConfiguration = {
    copy(resolutionCacheDir = resolutionCacheDir)
  }
  def withResolutionCacheDir(resolutionCacheDir: java.io.File): InlineIvyConfiguration = {
    copy(resolutionCacheDir = Option(resolutionCacheDir))
  }
}
object InlineIvyConfiguration {
  /** Provided for backward compatibility. */
  @deprecated("Use an alternative apply", "1.2.0")
  def apply(
  paths:                sbt.librarymanagement.ivy.IvyPaths,
  resolvers:            Vector[sbt.librarymanagement.Resolver],
  otherResolvers:       Vector[sbt.librarymanagement.Resolver],
  moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration],
  lock:                 Option[xsbti.GlobalLock],
  checksums:            Vector[String],
  managedChecksums:     Boolean,
  resolutionCacheDir:   Option[java.io.File],
  updateOptions:        sbt.librarymanagement.ivy.UpdateOptions,
  log:                  xsbti.Logger
  ): InlineIvyConfiguration = {
    apply()
    .withLock(lock)
    .withResolvers(resolvers)
    .withOtherResolvers(otherResolvers)
    .withModuleConfigurations(moduleConfigurations)
    .withChecksums(checksums)
    .withManagedChecksums(managedChecksums)
    .withResolutionCacheDir(resolutionCacheDir)
    .withLog(log)
  }
  def apply(): InlineIvyConfiguration = new InlineIvyConfiguration()
  def apply(lock: Option[xsbti.GlobalLock], log: Option[xsbti.Logger], updateOptions: sbt.librarymanagement.ivy.UpdateOptions, paths: Option[sbt.librarymanagement.ivy.IvyPaths], resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration], checksums: Vector[String], managedChecksums: Boolean, resolutionCacheDir: Option[java.io.File]): InlineIvyConfiguration = new InlineIvyConfiguration(lock, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
  def apply(lock: xsbti.GlobalLock, log: xsbti.Logger, updateOptions: sbt.librarymanagement.ivy.UpdateOptions, paths: sbt.librarymanagement.ivy.IvyPaths, resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration], checksums: Vector[String], managedChecksums: Boolean, resolutionCacheDir: java.io.File): InlineIvyConfiguration = new InlineIvyConfiguration(Option(lock), Option(log), updateOptions, Option(paths), resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, Option(resolutionCacheDir))
}
