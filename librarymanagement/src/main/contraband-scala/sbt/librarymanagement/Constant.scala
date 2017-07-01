/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
/** Cross-versions a module using the string `value`. */
final class Constant private (
  val value: String) extends sbt.librarymanagement.CrossVersion() with Serializable {
  
  private def this() = this("")
  
  override def equals(o: Any): Boolean = o match {
    case x: Constant => (this.value == x.value)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "Constant".##) + value.##)
  }
  override def toString: String = {
    "Constant(" + value + ")"
  }
  protected[this] def copy(value: String = value): Constant = {
    new Constant(value)
  }
  def withValue(value: String): Constant = {
    copy(value = value)
  }
}
object Constant {
  
  def apply(): Constant = new Constant("")
  def apply(value: String): Constant = new Constant(value)
}
