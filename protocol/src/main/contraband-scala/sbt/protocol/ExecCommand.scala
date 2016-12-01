/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Command to execute sbt command. */
final class ExecCommand private (
  val commandLine: String) extends sbt.protocol.CommandMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecCommand => (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (17 + commandLine.##)
  }
  override def toString: String = {
    "ExecCommand(" + commandLine + ")"
  }
  protected[this] def copy(commandLine: String = commandLine): ExecCommand = {
    new ExecCommand(commandLine)
  }
  def withCommandLine(commandLine: String): ExecCommand = {
    copy(commandLine = commandLine)
  }
}
object ExecCommand {
  
  def apply(commandLine: String): ExecCommand = new ExecCommand(commandLine)
}
