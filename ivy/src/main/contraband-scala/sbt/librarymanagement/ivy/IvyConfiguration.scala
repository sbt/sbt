/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
abstract class IvyConfiguration(
  val lock: Option[xsbti.GlobalLock],
  val log: Option[xsbti.Logger],
  val updateOptions: sbt.librarymanagement.ivy.UpdateOptions) extends Serializable {
  
  def this() = this(None, None, sbt.librarymanagement.ivy.UpdateOptions())
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: IvyConfiguration => (this.lock == x.lock) && (this.log == x.log) && (this.updateOptions == x.updateOptions)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.librarymanagement.ivy.IvyConfiguration".##) + lock.##) + log.##) + updateOptions.##)
  }
  override def toString: String = {
    "IvyConfiguration(" + lock + ", " + log + ", " + updateOptions + ")"
  }
}
object IvyConfiguration {
  
}
