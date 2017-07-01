/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class ExternalIvyConfiguration private (
  lock: Option[xsbti.GlobalLock],
  baseDirectory: java.io.File,
  log: xsbti.Logger,
  updateOptions: sbt.librarymanagement.UpdateOptions,
  val uri: java.net.URI,
  val extraResolvers: Vector[sbt.librarymanagement.Resolver]) extends sbt.internal.librarymanagement.IvyConfiguration(lock, baseDirectory, log, updateOptions) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExternalIvyConfiguration => (this.lock == x.lock) && (this.baseDirectory == x.baseDirectory) && (this.log == x.log) && (this.updateOptions == x.updateOptions) && (this.uri == x.uri) && (this.extraResolvers == x.extraResolvers)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "ExternalIvyConfiguration".##) + lock.##) + baseDirectory.##) + log.##) + updateOptions.##) + uri.##) + extraResolvers.##)
  }
  override def toString: String = {
    "ExternalIvyConfiguration(" + lock + ", " + baseDirectory + ", " + log + ", " + updateOptions + ", " + uri + ", " + extraResolvers + ")"
  }
  protected[this] def copy(lock: Option[xsbti.GlobalLock] = lock, baseDirectory: java.io.File = baseDirectory, log: xsbti.Logger = log, updateOptions: sbt.librarymanagement.UpdateOptions = updateOptions, uri: java.net.URI = uri, extraResolvers: Vector[sbt.librarymanagement.Resolver] = extraResolvers): ExternalIvyConfiguration = {
    new ExternalIvyConfiguration(lock, baseDirectory, log, updateOptions, uri, extraResolvers)
  }
  def withLock(lock: Option[xsbti.GlobalLock]): ExternalIvyConfiguration = {
    copy(lock = lock)
  }
  def withLock(lock: xsbti.GlobalLock): ExternalIvyConfiguration = {
    copy(lock = Option(lock))
  }
  def withBaseDirectory(baseDirectory: java.io.File): ExternalIvyConfiguration = {
    copy(baseDirectory = baseDirectory)
  }
  def withLog(log: xsbti.Logger): ExternalIvyConfiguration = {
    copy(log = log)
  }
  def withUpdateOptions(updateOptions: sbt.librarymanagement.UpdateOptions): ExternalIvyConfiguration = {
    copy(updateOptions = updateOptions)
  }
  def withUri(uri: java.net.URI): ExternalIvyConfiguration = {
    copy(uri = uri)
  }
  def withExtraResolvers(extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = {
    copy(extraResolvers = extraResolvers)
  }
}
object ExternalIvyConfiguration {
  
  def apply(lock: Option[xsbti.GlobalLock], baseDirectory: java.io.File, log: xsbti.Logger, updateOptions: sbt.librarymanagement.UpdateOptions, uri: java.net.URI, extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = new ExternalIvyConfiguration(lock, baseDirectory, log, updateOptions, uri, extraResolvers)
  def apply(lock: xsbti.GlobalLock, baseDirectory: java.io.File, log: xsbti.Logger, updateOptions: sbt.librarymanagement.UpdateOptions, uri: java.net.URI, extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = new ExternalIvyConfiguration(Option(lock), baseDirectory, log, updateOptions, uri, extraResolvers)
}
