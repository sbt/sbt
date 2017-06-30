/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
final class TraceEvent private (
  val level: String,
  val message: Throwable,
  channelName: Option[String],
  execId: Option[String]) extends sbt.internal.util.AbstractEntry(channelName, execId) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TraceEvent => (this.level == x.level) && (this.message == x.message) && (this.channelName == x.channelName) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "TraceEvent".##) + level.##) + message.##) + channelName.##) + execId.##)
  }
  override def toString: String = {
    "TraceEvent(" + level + ", " + message + ", " + channelName + ", " + execId + ")"
  }
  protected[this] def copy(level: String = level, message: Throwable = message, channelName: Option[String] = channelName, execId: Option[String] = execId): TraceEvent = {
    new TraceEvent(level, message, channelName, execId)
  }
  def withLevel(level: String): TraceEvent = {
    copy(level = level)
  }
  def withMessage(message: Throwable): TraceEvent = {
    copy(message = message)
  }
  def withChannelName(channelName: Option[String]): TraceEvent = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): TraceEvent = {
    copy(channelName = Option(channelName))
  }
  def withExecId(execId: Option[String]): TraceEvent = {
    copy(execId = execId)
  }
  def withExecId(execId: String): TraceEvent = {
    copy(execId = Option(execId))
  }
}
object TraceEvent {
  
  def apply(level: String, message: Throwable, channelName: Option[String], execId: Option[String]): TraceEvent = new TraceEvent(level, message, channelName, execId)
  def apply(level: String, message: Throwable, channelName: String, execId: String): TraceEvent = new TraceEvent(level, message, Option(channelName), Option(execId))
}
