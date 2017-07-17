/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Command to execute sbt command. */
final class ExecCommand private (
  val commandLine: String,
  val execId: Option[String]) extends sbt.protocol.CommandMessage() with Serializable {
  
  private def this(commandLine: String) = this(commandLine, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecCommand => (this.commandLine == x.commandLine) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.ExecCommand".##) + commandLine.##) + execId.##)
  }
  override def toString: String = {
    "ExecCommand(" + commandLine + ", " + execId + ")"
  }
  protected[this] def copy(commandLine: String = commandLine, execId: Option[String] = execId): ExecCommand = {
    new ExecCommand(commandLine, execId)
  }
  def withCommandLine(commandLine: String): ExecCommand = {
    copy(commandLine = commandLine)
  }
  def withExecId(execId: Option[String]): ExecCommand = {
    copy(execId = execId)
  }
  def withExecId(execId: String): ExecCommand = {
    copy(execId = Option(execId))
  }
}
object ExecCommand {
  
  def apply(commandLine: String): ExecCommand = new ExecCommand(commandLine, None)
  def apply(commandLine: String, execId: Option[String]): ExecCommand = new ExecCommand(commandLine, execId)
  def apply(commandLine: String, execId: String): ExecCommand = new ExecCommand(commandLine, Option(execId))
}
