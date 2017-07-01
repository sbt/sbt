/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
final class RetrieveConfiguration private (
  val retrieveDirectory: java.io.File,
  val outputPattern: String,
  val sync: Boolean,
  val configurationsToRetrieve: Option[Set[sbt.librarymanagement.Configuration]]) extends Serializable {
  
  private def this(retrieveDirectory: java.io.File, outputPattern: String) = this(retrieveDirectory, outputPattern, false, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: RetrieveConfiguration => (this.retrieveDirectory == x.retrieveDirectory) && (this.outputPattern == x.outputPattern) && (this.sync == x.sync) && (this.configurationsToRetrieve == x.configurationsToRetrieve)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "RetrieveConfiguration".##) + retrieveDirectory.##) + outputPattern.##) + sync.##) + configurationsToRetrieve.##)
  }
  override def toString: String = {
    "RetrieveConfiguration(" + retrieveDirectory + ", " + outputPattern + ", " + sync + ", " + configurationsToRetrieve + ")"
  }
  protected[this] def copy(retrieveDirectory: java.io.File = retrieveDirectory, outputPattern: String = outputPattern, sync: Boolean = sync, configurationsToRetrieve: Option[Set[sbt.librarymanagement.Configuration]] = configurationsToRetrieve): RetrieveConfiguration = {
    new RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
  }
  def withRetrieveDirectory(retrieveDirectory: java.io.File): RetrieveConfiguration = {
    copy(retrieveDirectory = retrieveDirectory)
  }
  def withOutputPattern(outputPattern: String): RetrieveConfiguration = {
    copy(outputPattern = outputPattern)
  }
  def withSync(sync: Boolean): RetrieveConfiguration = {
    copy(sync = sync)
  }
  def withConfigurationsToRetrieve(configurationsToRetrieve: Option[Set[sbt.librarymanagement.Configuration]]): RetrieveConfiguration = {
    copy(configurationsToRetrieve = configurationsToRetrieve)
  }
}
object RetrieveConfiguration {
  
  def apply(retrieveDirectory: java.io.File, outputPattern: String): RetrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, outputPattern, false, None)
  def apply(retrieveDirectory: java.io.File, outputPattern: String, sync: Boolean, configurationsToRetrieve: Option[Set[sbt.librarymanagement.Configuration]]): RetrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
}
