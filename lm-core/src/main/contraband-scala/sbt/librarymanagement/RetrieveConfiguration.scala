/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
final class RetrieveConfiguration private (
  val retrieveDirectory: Option[java.io.File],
  val outputPattern: Option[String],
  val sync: Boolean,
  val configurationsToRetrieve: Option[scala.Vector[sbt.librarymanagement.ConfigRef]]) extends Serializable {
  
  private def this() = this(None, None, false, None)
  private def this(retrieveDirectory: Option[java.io.File], outputPattern: Option[String]) = this(retrieveDirectory, outputPattern, false, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RetrieveConfiguration => (this.retrieveDirectory == x.retrieveDirectory) && (this.outputPattern == x.outputPattern) && (this.sync == x.sync) && (this.configurationsToRetrieve == x.configurationsToRetrieve)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.RetrieveConfiguration".##) + retrieveDirectory.##) + outputPattern.##) + sync.##) + configurationsToRetrieve.##)
  }
  override def toString: String = {
    "RetrieveConfiguration(" + retrieveDirectory + ", " + outputPattern + ", " + sync + ", " + configurationsToRetrieve + ")"
  }
  private[this] def copy(retrieveDirectory: Option[java.io.File] = retrieveDirectory, outputPattern: Option[String] = outputPattern, sync: Boolean = sync, configurationsToRetrieve: Option[scala.Vector[sbt.librarymanagement.ConfigRef]] = configurationsToRetrieve): RetrieveConfiguration = {
    new RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
  }
  def withRetrieveDirectory(retrieveDirectory: Option[java.io.File]): RetrieveConfiguration = {
    copy(retrieveDirectory = retrieveDirectory)
  }
  def withRetrieveDirectory(retrieveDirectory: java.io.File): RetrieveConfiguration = {
    copy(retrieveDirectory = Option(retrieveDirectory))
  }
  def withOutputPattern(outputPattern: Option[String]): RetrieveConfiguration = {
    copy(outputPattern = outputPattern)
  }
  def withOutputPattern(outputPattern: String): RetrieveConfiguration = {
    copy(outputPattern = Option(outputPattern))
  }
  def withSync(sync: Boolean): RetrieveConfiguration = {
    copy(sync = sync)
  }
  def withConfigurationsToRetrieve(configurationsToRetrieve: Option[scala.Vector[sbt.librarymanagement.ConfigRef]]): RetrieveConfiguration = {
    copy(configurationsToRetrieve = configurationsToRetrieve)
  }
  def withConfigurationsToRetrieve(configurationsToRetrieve: scala.Vector[sbt.librarymanagement.ConfigRef]): RetrieveConfiguration = {
    copy(configurationsToRetrieve = Option(configurationsToRetrieve))
  }
}
object RetrieveConfiguration {
  
  def apply(): RetrieveConfiguration = new RetrieveConfiguration()
  def apply(retrieveDirectory: Option[java.io.File], outputPattern: Option[String]): RetrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, outputPattern)
  def apply(retrieveDirectory: java.io.File, outputPattern: String): RetrieveConfiguration = new RetrieveConfiguration(Option(retrieveDirectory), Option(outputPattern))
  def apply(retrieveDirectory: Option[java.io.File], outputPattern: Option[String], sync: Boolean, configurationsToRetrieve: Option[scala.Vector[sbt.librarymanagement.ConfigRef]]): RetrieveConfiguration = new RetrieveConfiguration(retrieveDirectory, outputPattern, sync, configurationsToRetrieve)
  def apply(retrieveDirectory: java.io.File, outputPattern: String, sync: Boolean, configurationsToRetrieve: scala.Vector[sbt.librarymanagement.ConfigRef]): RetrieveConfiguration = new RetrieveConfiguration(Option(retrieveDirectory), Option(outputPattern), sync, Option(configurationsToRetrieve))
}
