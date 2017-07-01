/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.librarymanagement
abstract class IvyConfiguration(
  val lock: Option[xsbti.GlobalLock],
  val baseDirectory: java.io.File,
  val log: xsbti.Logger,
  val updateOptions: sbt.librarymanagement.UpdateOptions) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: IvyConfiguration => (this.lock == x.lock) && (this.baseDirectory == x.baseDirectory) && (this.log == x.log) && (this.updateOptions == x.updateOptions)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "IvyConfiguration".##) + lock.##) + baseDirectory.##) + log.##) + updateOptions.##)
  }
  override def toString: String = {
    "IvyConfiguration(" + lock + ", " + baseDirectory + ", " + log + ", " + updateOptions + ")"
  }
}
object IvyConfiguration {
  
}
