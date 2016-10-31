/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.server
final class EventMessage(
  val `type`: String,
  val status: Option[String],
  val commandQueue: Vector[String],
  val level: Option[String],
  val message: Option[String],
  val success: Option[Boolean],
  val commandLine: Option[String]) extends Serializable {
  
  def this(`type`: String) = this(`type`, None, Vector(), None, None, None, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: EventMessage => (this.`type` == x.`type`) && (this.status == x.status) && (this.commandQueue == x.commandQueue) && (this.level == x.level) && (this.message == x.message) && (this.success == x.success) && (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + `type`.##) + status.##) + commandQueue.##) + level.##) + message.##) + success.##) + commandLine.##)
  }
  override def toString: String = {
    "EventMessage(" + `type` + ", " + status + ", " + commandQueue + ", " + level + ", " + message + ", " + success + ", " + commandLine + ")"
  }
  def copy(`type`: String): EventMessage = {
    new EventMessage(`type`, status, commandQueue, level, message, success, commandLine)
  }
  def copy(`type`: String = `type`, status: Option[String] = status, commandQueue: Vector[String] = commandQueue, level: Option[String] = level, message: Option[String] = message, success: Option[Boolean] = success, commandLine: Option[String] = commandLine): EventMessage = {
    new EventMessage(`type`, status, commandQueue, level, message, success, commandLine)
  }
  def withType(`type`: String): EventMessage = {
    copy(`type` = `type`)
  }
  def withStatus(status: Option[String]): EventMessage = {
    copy(status = status)
  }
  def withCommandQueue(commandQueue: Vector[String]): EventMessage = {
    copy(commandQueue = commandQueue)
  }
  def withLevel(level: Option[String]): EventMessage = {
    copy(level = level)
  }
  def withMessage(message: Option[String]): EventMessage = {
    copy(message = message)
  }
  def withSuccess(success: Option[Boolean]): EventMessage = {
    copy(success = success)
  }
  def withCommandLine(commandLine: Option[String]): EventMessage = {
    copy(commandLine = commandLine)
  }
}
object EventMessage {
  def apply(`type`: String): EventMessage = new EventMessage(`type`, None, Vector(), None, None, None, None)
  def apply(`type`: String, status: Option[String], commandQueue: Vector[String], level: Option[String], message: Option[String], success: Option[Boolean], commandLine: Option[String]): EventMessage = new EventMessage(`type`, status, commandQueue, level, message, success, commandLine)
}
