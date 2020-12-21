/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
abstract class ModuleSettings(
  val validate: Boolean,
  val scalaModuleInfo: Option[sbt.librarymanagement.ScalaModuleInfo]) extends Serializable {
  
  def this() = this(false, None)
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ModuleSettings => (this.validate == x.validate) && (this.scalaModuleInfo == x.scalaModuleInfo)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.librarymanagement.ModuleSettings".##) + validate.##) + scalaModuleInfo.##)
  }
  override def toString: String = {
    "ModuleSettings(" + validate + ", " + scalaModuleInfo + ")"
  }
}
object ModuleSettings {
  
}
