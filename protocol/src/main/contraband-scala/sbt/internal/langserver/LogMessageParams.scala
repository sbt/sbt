/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class LogMessageParams private (
  /** The message type. */
  val `type`: Long,
  /** The actual message */
  val message: String) extends Serializable {
  
  
  
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
  private[this] def copy(`type`: Long = `type`, message: String = message): LogMessageParams = {
    new LogMessageParams(`type`, message)
  }
  def withType(`type`: Long): LogMessageParams = {
    copy(`type` = `type`)
  }
  def withMessage(message: String): LogMessageParams = {
    copy(message = message)
  }
}
object LogMessageParams {
  
  def apply(`type`: Long, message: String): LogMessageParams = new LogMessageParams(`type`, message)
}
