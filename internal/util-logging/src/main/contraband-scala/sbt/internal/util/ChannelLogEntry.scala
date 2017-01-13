/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
final class ChannelLogEntry private (
  val level: String,
  val message: String,
  channelName: Option[String],
  execId: Option[String]) extends sbt.internal.util.AbstractEntry(channelName, execId) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ChannelLogEntry => (this.level == x.level) && (this.message == x.message) && (this.channelName == x.channelName) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + level.##) + message.##) + channelName.##) + execId.##)
  }
  override def toString: String = {
    "ChannelLogEntry(" + level + ", " + message + ", " + channelName + ", " + execId + ")"
  }
  protected[this] def copy(level: String = level, message: String = message, channelName: Option[String] = channelName, execId: Option[String] = execId): ChannelLogEntry = {
    new ChannelLogEntry(level, message, channelName, execId)
  }
  def withLevel(level: String): ChannelLogEntry = {
    copy(level = level)
  }
  def withMessage(message: String): ChannelLogEntry = {
    copy(message = message)
  }
  def withChannelName(channelName: Option[String]): ChannelLogEntry = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): ChannelLogEntry = {
    copy(channelName = Option(channelName))
  }
  def withExecId(execId: Option[String]): ChannelLogEntry = {
    copy(execId = execId)
  }
  def withExecId(execId: String): ChannelLogEntry = {
    copy(execId = Option(execId))
  }
}
object ChannelLogEntry {
  
  def apply(level: String, message: String, channelName: Option[String], execId: Option[String]): ChannelLogEntry = new ChannelLogEntry(level, message, channelName, execId)
  def apply(level: String, message: String, channelName: String, execId: String): ChannelLogEntry = new ChannelLogEntry(level, message, Option(channelName), Option(execId))
}
