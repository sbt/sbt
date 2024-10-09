/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Configuration specific to an Ivy filesystem resolver. */
final class FileConfiguration private (
  val isLocal: Boolean,
  val isTransactional: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: FileConfiguration => (this.isLocal == x.isLocal) && (this.isTransactional == x.isTransactional)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.FileConfiguration".##) + isLocal.##) + isTransactional.##)
  }
  override def toString: String = {
    "FileConfiguration(" + isLocal + ", " + isTransactional + ")"
  }
  private[this] def copy(isLocal: Boolean = isLocal, isTransactional: Option[Boolean] = isTransactional): FileConfiguration = {
    new FileConfiguration(isLocal, isTransactional)
  }
  def withIsLocal(isLocal: Boolean): FileConfiguration = {
    copy(isLocal = isLocal)
  }
  def withIsTransactional(isTransactional: Option[Boolean]): FileConfiguration = {
    copy(isTransactional = isTransactional)
  }
  def withIsTransactional(isTransactional: Boolean): FileConfiguration = {
    copy(isTransactional = Option(isTransactional))
  }
}
object FileConfiguration {
  
  def apply(isLocal: Boolean, isTransactional: Option[Boolean]): FileConfiguration = new FileConfiguration(isLocal, isTransactional)
  def apply(isLocal: Boolean, isTransactional: Boolean): FileConfiguration = new FileConfiguration(isLocal, Option(isTransactional))
}
