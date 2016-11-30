/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.server
final class CommandMessage private (
  val `type`: String,
  val commandLine: Option[String]) extends Serializable {
  
  private def this(`type`: String) = this(`type`, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: CommandMessage => (this.`type` == x.`type`) && (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + `type`.##) + commandLine.##)
  }
  override def toString: String = {
    "CommandMessage(" + `type` + ", " + commandLine + ")"
  }
  protected[this] def copy(`type`: String = `type`, commandLine: Option[String] = commandLine): CommandMessage = {
    new CommandMessage(`type`, commandLine)
  }
  def withType(`type`: String): CommandMessage = {
    copy(`type` = `type`)
  }
  def withCommandLine(commandLine: Option[String]): CommandMessage = {
    copy(commandLine = commandLine)
  }
}
object CommandMessage {
  
  def apply(`type`: String): CommandMessage = new CommandMessage(`type`, None)
  def apply(`type`: String, commandLine: Option[String]): CommandMessage = new CommandMessage(`type`, commandLine)
}
