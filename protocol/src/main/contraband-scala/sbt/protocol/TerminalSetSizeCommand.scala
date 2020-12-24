/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetSizeCommand private (
  val width: Int,
  val height: Int) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TerminalSetSizeCommand => (this.width == x.width) && (this.height == x.height)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.TerminalSetSizeCommand".##) + width.##) + height.##)
  }
  override def toString: String = {
    "TerminalSetSizeCommand(" + width + ", " + height + ")"
  }
  private[this] def copy(width: Int = width, height: Int = height): TerminalSetSizeCommand = {
    new TerminalSetSizeCommand(width, height)
  }
  def withWidth(width: Int): TerminalSetSizeCommand = {
    copy(width = width)
  }
  def withHeight(height: Int): TerminalSetSizeCommand = {
    copy(height = height)
  }
}
object TerminalSetSizeCommand {
  
  def apply(width: Int, height: Int): TerminalSetSizeCommand = new TerminalSetSizeCommand(width, height)
}
