/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetRawModeCommand private (
  val toggle: Boolean) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TerminalSetRawModeCommand => (this.toggle == x.toggle)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.TerminalSetRawModeCommand".##) + toggle.##)
  }
  override def toString: String = {
    "TerminalSetRawModeCommand(" + toggle + ")"
  }
  private[this] def copy(toggle: Boolean = toggle): TerminalSetRawModeCommand = {
    new TerminalSetRawModeCommand(toggle)
  }
  def withToggle(toggle: Boolean): TerminalSetRawModeCommand = {
    copy(toggle = toggle)
  }
}
object TerminalSetRawModeCommand {
  
  def apply(toggle: Boolean): TerminalSetRawModeCommand = new TerminalSetRawModeCommand(toggle)
}
