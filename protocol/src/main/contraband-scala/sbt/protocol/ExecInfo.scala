/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** An exec requested by a client, either running or waiting. */
final class ExecInfo private (
  val id: String,
  val channelName: String,
  val clientName: String,
  val commandLine: String,
  val status: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ExecInfo => (this.id == x.id) && (this.channelName == x.channelName) && (this.clientName == x.clientName) && (this.commandLine == x.commandLine) && (this.status == x.status)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.ExecInfo".##) + id.##) + channelName.##) + clientName.##) + commandLine.##) + status.##)
  }
  override def toString: String = {
    "ExecInfo(" + id + ", " + channelName + ", " + clientName + ", " + commandLine + ", " + status + ")"
  }
  private def copy(id: String = id, channelName: String = channelName, clientName: String = clientName, commandLine: String = commandLine, status: String = status): ExecInfo = {
    new ExecInfo(id, channelName, clientName, commandLine, status)
  }
  def withId(id: String): ExecInfo = {
    copy(id = id)
  }
  def withChannelName(channelName: String): ExecInfo = {
    copy(channelName = channelName)
  }
  def withClientName(clientName: String): ExecInfo = {
    copy(clientName = clientName)
  }
  def withCommandLine(commandLine: String): ExecInfo = {
    copy(commandLine = commandLine)
  }
  def withStatus(status: String): ExecInfo = {
    copy(status = status)
  }
}
object ExecInfo {
  
  def apply(id: String, channelName: String, clientName: String, commandLine: String, status: String): ExecInfo = new ExecInfo(id, channelName, clientName, commandLine, status)
}
