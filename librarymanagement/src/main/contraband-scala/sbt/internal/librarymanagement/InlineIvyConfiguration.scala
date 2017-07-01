/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class InlineIvyConfiguration private (
  lock: Option[xsbti.GlobalLock],
  baseDirectory: java.io.File,
  log: xsbti.Logger,
  updateOptions: sbt.librarymanagement.UpdateOptions,
  val paths: sbt.internal.librarymanagement.IvyPaths,
  val resolvers: Vector[sbt.librarymanagement.Resolver],
  val otherResolvers: Vector[sbt.librarymanagement.Resolver],
  val moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration],
  val checksums: Vector[String],
  val managedChecksums: Boolean,
  val resolutionCacheDir: Option[java.io.File]) extends sbt.internal.librarymanagement.IvyConfiguration(lock, baseDirectory, log, updateOptions) with Serializable {
  def this(
  paths:                sbt.internal.librarymanagement.IvyPaths,
  resolvers:            Vector[sbt.librarymanagement.Resolver],
  otherResolvers:       Vector[sbt.librarymanagement.Resolver],
  moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration],
  lock:                 Option[xsbti.GlobalLock],
  checksums:            Vector[String],
  managedChecksums:     Boolean,
  resolutionCacheDir:   Option[java.io.File],
  updateOptions:        sbt.librarymanagement.UpdateOptions,
  log:                  xsbti.Logger
  ) =
  this(lock, paths.baseDirectory, log, updateOptions, paths, resolvers, otherResolvers,
  moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
  
  
  override def equals(o: Any): Boolean = o match {
    case x: InlineIvyConfiguration => (this.lock == x.lock) && (this.baseDirectory == x.baseDirectory) && (this.log == x.log) && (this.updateOptions == x.updateOptions) && (this.paths == x.paths) && (this.resolvers == x.resolvers) && (this.otherResolvers == x.otherResolvers) && (this.moduleConfigurations == x.moduleConfigurations) && (this.checksums == x.checksums) && (this.managedChecksums == x.managedChecksums) && (this.resolutionCacheDir == x.resolutionCacheDir)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "InlineIvyConfiguration".##) + lock.##) + baseDirectory.##) + log.##) + updateOptions.##) + paths.##) + resolvers.##) + otherResolvers.##) + moduleConfigurations.##) + checksums.##) + managedChecksums.##) + resolutionCacheDir.##)
  }
  override def toString: String = {
    "InlineIvyConfiguration(" + lock + ", " + baseDirectory + ", " + log + ", " + updateOptions + ", " + paths + ", " + resolvers + ", " + otherResolvers + ", " + moduleConfigurations + ", " + checksums + ", " + managedChecksums + ", " + resolutionCacheDir + ")"
  }
  protected[this] def copy(lock: Option[xsbti.GlobalLock] = lock, baseDirectory: java.io.File = baseDirectory, log: xsbti.Logger = log, updateOptions: sbt.librarymanagement.UpdateOptions = updateOptions, paths: sbt.internal.librarymanagement.IvyPaths = paths, resolvers: Vector[sbt.librarymanagement.Resolver] = resolvers, otherResolvers: Vector[sbt.librarymanagement.Resolver] = otherResolvers, moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration] = moduleConfigurations, checksums: Vector[String] = checksums, managedChecksums: Boolean = managedChecksums, resolutionCacheDir: Option[java.io.File] = resolutionCacheDir): InlineIvyConfiguration = {
    new InlineIvyConfiguration(lock, baseDirectory, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
  }
  def withLock(lock: Option[xsbti.GlobalLock]): InlineIvyConfiguration = {
    copy(lock = lock)
  }
  def withLock(lock: xsbti.GlobalLock): InlineIvyConfiguration = {
    copy(lock = Option(lock))
  }
  def withBaseDirectory(baseDirectory: java.io.File): InlineIvyConfiguration = {
    copy(baseDirectory = baseDirectory)
  }
  def withLog(log: xsbti.Logger): InlineIvyConfiguration = {
    copy(log = log)
  }
  def withUpdateOptions(updateOptions: sbt.librarymanagement.UpdateOptions): InlineIvyConfiguration = {
    copy(updateOptions = updateOptions)
  }
  def withPaths(paths: sbt.internal.librarymanagement.IvyPaths): InlineIvyConfiguration = {
    copy(paths = paths)
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
  
  def apply(lock: Option[xsbti.GlobalLock], baseDirectory: java.io.File, log: xsbti.Logger, updateOptions: sbt.librarymanagement.UpdateOptions, paths: sbt.internal.librarymanagement.IvyPaths, resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration], checksums: Vector[String], managedChecksums: Boolean, resolutionCacheDir: Option[java.io.File]): InlineIvyConfiguration = new InlineIvyConfiguration(lock, baseDirectory, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, resolutionCacheDir)
  def apply(lock: xsbti.GlobalLock, baseDirectory: java.io.File, log: xsbti.Logger, updateOptions: sbt.librarymanagement.UpdateOptions, paths: sbt.internal.librarymanagement.IvyPaths, resolvers: Vector[sbt.librarymanagement.Resolver], otherResolvers: Vector[sbt.librarymanagement.Resolver], moduleConfigurations: Vector[sbt.librarymanagement.ModuleConfiguration], checksums: Vector[String], managedChecksums: Boolean, resolutionCacheDir: java.io.File): InlineIvyConfiguration = new InlineIvyConfiguration(Option(lock), baseDirectory, log, updateOptions, paths, resolvers, otherResolvers, moduleConfigurations, checksums, managedChecksums, Option(resolutionCacheDir))
}
