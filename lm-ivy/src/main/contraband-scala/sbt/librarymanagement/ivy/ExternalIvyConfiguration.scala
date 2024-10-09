/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
final class ExternalIvyConfiguration private (
  lock: Option[xsbti.GlobalLock],
  log: Option[xsbti.Logger],
  updateOptions: sbt.librarymanagement.ivy.UpdateOptions,
  val baseDirectory: Option[java.io.File],
  val uri: Option[java.net.URI],
  val extraResolvers: Vector[sbt.librarymanagement.Resolver]) extends sbt.librarymanagement.ivy.IvyConfiguration(lock, log, updateOptions) with Serializable {
  
  private def this() = this(None, None, sbt.librarymanagement.ivy.UpdateOptions(), None, None, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ExternalIvyConfiguration => (this.lock == x.lock) && (this.log == x.log) && (this.updateOptions == x.updateOptions) && (this.baseDirectory == x.baseDirectory) && (this.uri == x.uri) && (this.extraResolvers == x.extraResolvers)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ivy.ExternalIvyConfiguration".##) + lock.##) + log.##) + updateOptions.##) + baseDirectory.##) + uri.##) + extraResolvers.##)
  }
  override def toString: String = {
    "ExternalIvyConfiguration(" + lock + ", " + log + ", " + updateOptions + ", " + baseDirectory + ", " + uri + ", " + extraResolvers + ")"
  }
  private[this] def copy(lock: Option[xsbti.GlobalLock] = lock, log: Option[xsbti.Logger] = log, updateOptions: sbt.librarymanagement.ivy.UpdateOptions = updateOptions, baseDirectory: Option[java.io.File] = baseDirectory, uri: Option[java.net.URI] = uri, extraResolvers: Vector[sbt.librarymanagement.Resolver] = extraResolvers): ExternalIvyConfiguration = {
    new ExternalIvyConfiguration(lock, log, updateOptions, baseDirectory, uri, extraResolvers)
  }
  def withLock(lock: Option[xsbti.GlobalLock]): ExternalIvyConfiguration = {
    copy(lock = lock)
  }
  def withLock(lock: xsbti.GlobalLock): ExternalIvyConfiguration = {
    copy(lock = Option(lock))
  }
  def withLog(log: Option[xsbti.Logger]): ExternalIvyConfiguration = {
    copy(log = log)
  }
  def withLog(log: xsbti.Logger): ExternalIvyConfiguration = {
    copy(log = Option(log))
  }
  def withUpdateOptions(updateOptions: sbt.librarymanagement.ivy.UpdateOptions): ExternalIvyConfiguration = {
    copy(updateOptions = updateOptions)
  }
  def withBaseDirectory(baseDirectory: Option[java.io.File]): ExternalIvyConfiguration = {
    copy(baseDirectory = baseDirectory)
  }
  def withBaseDirectory(baseDirectory: java.io.File): ExternalIvyConfiguration = {
    copy(baseDirectory = Option(baseDirectory))
  }
  def withUri(uri: Option[java.net.URI]): ExternalIvyConfiguration = {
    copy(uri = uri)
  }
  def withUri(uri: java.net.URI): ExternalIvyConfiguration = {
    copy(uri = Option(uri))
  }
  def withExtraResolvers(extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = {
    copy(extraResolvers = extraResolvers)
  }
}
object ExternalIvyConfiguration {
  
  def apply(): ExternalIvyConfiguration = new ExternalIvyConfiguration()
  def apply(lock: Option[xsbti.GlobalLock], log: Option[xsbti.Logger], updateOptions: sbt.librarymanagement.ivy.UpdateOptions, baseDirectory: Option[java.io.File], uri: Option[java.net.URI], extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = new ExternalIvyConfiguration(lock, log, updateOptions, baseDirectory, uri, extraResolvers)
  def apply(lock: xsbti.GlobalLock, log: xsbti.Logger, updateOptions: sbt.librarymanagement.ivy.UpdateOptions, baseDirectory: java.io.File, uri: java.net.URI, extraResolvers: Vector[sbt.librarymanagement.Resolver]): ExternalIvyConfiguration = new ExternalIvyConfiguration(Option(lock), Option(log), updateOptions, Option(baseDirectory), Option(uri), extraResolvers)
}
