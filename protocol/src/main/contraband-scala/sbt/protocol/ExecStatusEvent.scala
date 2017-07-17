/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Status event. */
final class ExecStatusEvent private (
  val status: String,
  val channelName: Option[String],
  val execId: Option[String],
  val commandQueue: Vector[String]) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecStatusEvent => (this.status == x.status) && (this.channelName == x.channelName) && (this.execId == x.execId) && (this.commandQueue == x.commandQueue)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.ExecStatusEvent".##) + status.##) + channelName.##) + execId.##) + commandQueue.##)
  }
  override def toString: String = {
    "ExecStatusEvent(" + status + ", " + channelName + ", " + execId + ", " + commandQueue + ")"
  }
  protected[this] def copy(status: String = status, channelName: Option[String] = channelName, execId: Option[String] = execId, commandQueue: Vector[String] = commandQueue): ExecStatusEvent = {
    new ExecStatusEvent(status, channelName, execId, commandQueue)
  }
  def withStatus(status: String): ExecStatusEvent = {
    copy(status = status)
  }
  def withChannelName(channelName: Option[String]): ExecStatusEvent = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): ExecStatusEvent = {
    copy(channelName = Option(channelName))
  }
  def withExecId(execId: Option[String]): ExecStatusEvent = {
    copy(execId = execId)
  }
  def withExecId(execId: String): ExecStatusEvent = {
    copy(execId = Option(execId))
  }
  def withCommandQueue(commandQueue: Vector[String]): ExecStatusEvent = {
    copy(commandQueue = commandQueue)
  }
}
object ExecStatusEvent {
  
  def apply(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String]): ExecStatusEvent = new ExecStatusEvent(status, channelName, execId, commandQueue)
  def apply(status: String, channelName: String, execId: String, commandQueue: Vector[String]): ExecStatusEvent = new ExecStatusEvent(status, Option(channelName), Option(execId), commandQueue)
}
