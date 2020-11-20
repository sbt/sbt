/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class TerminalSetEchoCommand private (
  val toggle: Boolean) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TerminalSetEchoCommand => (this.toggle == x.toggle)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.TerminalSetEchoCommand".##) + toggle.##)
  }
  override def toString: String = {
    "TerminalSetEchoCommand(" + toggle + ")"
  }
  private[this] def copy(toggle: Boolean = toggle): TerminalSetEchoCommand = {
    new TerminalSetEchoCommand(toggle)
  }
  def withToggle(toggle: Boolean): TerminalSetEchoCommand = {
    copy(toggle = toggle)
  }
}
object TerminalSetEchoCommand {
  
  def apply(toggle: Boolean): TerminalSetEchoCommand = new TerminalSetEchoCommand(toggle)
}
