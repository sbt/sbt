/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class LogMessageParams private (
  /** The message type. See MessageType. */
  val `type`: Option[Long],
  /** The actual message. */
  val message: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: LogMessageParams => (this.`type` == x.`type`) && (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.LogMessageParams".##) + `type`.##) + message.##)
  }
  override def toString: String = {
    "LogMessageParams(" + `type` + ", " + message + ")"
  }
  protected[this] def copy(`type`: Option[Long] = `type`, message: Option[String] = message): LogMessageParams = {
    new LogMessageParams(`type`, message)
  }
  def withType(`type`: Option[Long]): LogMessageParams = {
    copy(`type` = `type`)
  }
  def withType(`type`: Long): LogMessageParams = {
    copy(`type` = Option(`type`))
  }
  def withMessage(message: Option[String]): LogMessageParams = {
    copy(message = message)
  }
  def withMessage(message: String): LogMessageParams = {
    copy(message = Option(message))
  }
}
object LogMessageParams {
  
  def apply(`type`: Option[Long], message: Option[String]): LogMessageParams = new LogMessageParams(`type`, message)
  def apply(`type`: Long, message: String): LogMessageParams = new LogMessageParams(Option(`type`), Option(message))
}
