/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalGetSizeResponse private (
  val width: Int,
  val height: Int) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TerminalGetSizeResponse => (this.width == x.width) && (this.height == x.height)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.TerminalGetSizeResponse".##) + width.##) + height.##)
  }
  override def toString: String = {
    "TerminalGetSizeResponse(" + width + ", " + height + ")"
  }
  private[this] def copy(width: Int = width, height: Int = height): TerminalGetSizeResponse = {
    new TerminalGetSizeResponse(width, height)
  }
  def withWidth(width: Int): TerminalGetSizeResponse = {
    copy(width = width)
  }
  def withHeight(height: Int): TerminalGetSizeResponse = {
    copy(height = height)
  }
}
object TerminalGetSizeResponse {
  
  def apply(width: Int, height: Int): TerminalGetSizeResponse = new TerminalGetSizeResponse(width, height)
}
