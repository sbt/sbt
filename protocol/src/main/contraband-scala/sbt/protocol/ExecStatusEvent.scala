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
  val commandQueue: Vector[String],
  val exitCode: Option[Long],
  val message: Option[String]) extends sbt.protocol.EventMessage() with Serializable {
  
  private def this(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String]) = this(status, channelName, execId, commandQueue, None, None)
  private def this(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String], exitCode: Option[Long]) = this(status, channelName, execId, commandQueue, exitCode, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecStatusEvent => (this.status == x.status) && (this.channelName == x.channelName) && (this.execId == x.execId) && (this.commandQueue == x.commandQueue) && (this.exitCode == x.exitCode) && (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.protocol.ExecStatusEvent".##) + status.##) + channelName.##) + execId.##) + commandQueue.##) + exitCode.##) + message.##)
  }
  override def toString: String = {
    "ExecStatusEvent(" + status + ", " + channelName + ", " + execId + ", " + commandQueue + ", " + exitCode + ", " + message + ")"
  }
  private[this] def copy(status: String = status, channelName: Option[String] = channelName, execId: Option[String] = execId, commandQueue: Vector[String] = commandQueue, exitCode: Option[Long] = exitCode, message: Option[String] = message): ExecStatusEvent = {
    new ExecStatusEvent(status, channelName, execId, commandQueue, exitCode, message)
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
  def withExitCode(exitCode: Option[Long]): ExecStatusEvent = {
    copy(exitCode = exitCode)
  }
  def withExitCode(exitCode: Long): ExecStatusEvent = {
    copy(exitCode = Option(exitCode))
  }
  def withMessage(message: Option[String]): ExecStatusEvent = {
    copy(message = message)
  }
  def withMessage(message: String): ExecStatusEvent = {
    copy(message = Option(message))
  }
}
object ExecStatusEvent {
  
  def apply(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String]): ExecStatusEvent = new ExecStatusEvent(status, channelName, execId, commandQueue)
  def apply(status: String, channelName: String, execId: String, commandQueue: Vector[String]): ExecStatusEvent = new ExecStatusEvent(status, Option(channelName), Option(execId), commandQueue)
  def apply(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String], exitCode: Option[Long]): ExecStatusEvent = new ExecStatusEvent(status, channelName, execId, commandQueue, exitCode)
  def apply(status: String, channelName: String, execId: String, commandQueue: Vector[String], exitCode: Long): ExecStatusEvent = new ExecStatusEvent(status, Option(channelName), Option(execId), commandQueue, Option(exitCode))
  def apply(status: String, channelName: Option[String], execId: Option[String], commandQueue: Vector[String], exitCode: Option[Long], message: Option[String]): ExecStatusEvent = new ExecStatusEvent(status, channelName, execId, commandQueue, exitCode, message)
  def apply(status: String, channelName: String, execId: String, commandQueue: Vector[String], exitCode: Long, message: String): ExecStatusEvent = new ExecStatusEvent(status, Option(channelName), Option(execId), commandQueue, Option(exitCode), Option(message))
}
