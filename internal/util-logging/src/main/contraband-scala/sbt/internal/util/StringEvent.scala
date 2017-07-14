/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
final class StringEvent private (
  val level: String,
  val message: String,
  channelName: Option[String],
  execId: Option[String]) extends sbt.internal.util.AbstractEntry(channelName, execId) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: StringEvent => (this.level == x.level) && (this.message == x.message) && (this.channelName == x.channelName) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.util.StringEvent".##) + level.##) + message.##) + channelName.##) + execId.##)
  }
  override def toString: String = {
    "StringEvent(" + level + ", " + message + ", " + channelName + ", " + execId + ")"
  }
  protected[this] def copy(level: String = level, message: String = message, channelName: Option[String] = channelName, execId: Option[String] = execId): StringEvent = {
    new StringEvent(level, message, channelName, execId)
  }
  def withLevel(level: String): StringEvent = {
    copy(level = level)
  }
  def withMessage(message: String): StringEvent = {
    copy(message = message)
  }
  def withChannelName(channelName: Option[String]): StringEvent = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): StringEvent = {
    copy(channelName = Option(channelName))
  }
  def withExecId(execId: Option[String]): StringEvent = {
    copy(execId = execId)
  }
  def withExecId(execId: String): StringEvent = {
    copy(execId = Option(execId))
  }
}
object StringEvent {
  
  def apply(level: String, message: String, channelName: Option[String], execId: Option[String]): StringEvent = new StringEvent(level, message, channelName, execId)
  def apply(level: String, message: String, channelName: String, execId: String): StringEvent = new StringEvent(level, message, Option(channelName), Option(execId))
}
