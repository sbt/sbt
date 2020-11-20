/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class Attach private (
  val interactive: Boolean) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Attach => (this.interactive == x.interactive)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.Attach".##) + interactive.##)
  }
  override def toString: String = {
    "Attach(" + interactive + ")"
  }
  private[this] def copy(interactive: Boolean = interactive): Attach = {
    new Attach(interactive)
  }
  def withInteractive(interactive: Boolean): Attach = {
    copy(interactive = interactive)
  }
}
object Attach {
  
  def apply(interactive: Boolean): Attach = new Attach(interactive)
}
