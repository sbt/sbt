/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Log event. */
final class LogEvent private (
  val level: String,
  val message: String) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: LogEvent => (this.level == x.level) && (this.message == x.message)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.LogEvent".##) + level.##) + message.##)
  }
  override def toString: String = {
    "LogEvent(" + level + ", " + message + ")"
  }
  private[this] def copy(level: String = level, message: String = message): LogEvent = {
    new LogEvent(level, message)
  }
  def withLevel(level: String): LogEvent = {
    copy(level = level)
  }
  def withMessage(message: String): LogEvent = {
    copy(message = message)
  }
}
object LogEvent {
  
  def apply(level: String, message: String): LogEvent = new LogEvent(level, message)
}
