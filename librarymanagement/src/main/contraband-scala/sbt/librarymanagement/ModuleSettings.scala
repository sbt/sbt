/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
abstract class ModuleSettings(
  val validate: Boolean,
  val ivyScala: Option[sbt.librarymanagement.IvyScala]) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ModuleSettings => (this.validate == x.validate) && (this.ivyScala == x.ivyScala)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "ModuleSettings".##) + validate.##) + ivyScala.##)
  }
  override def toString: String = {
    "ModuleSettings(" + validate + ", " + ivyScala + ")"
  }
}
object ModuleSettings {
  
}
