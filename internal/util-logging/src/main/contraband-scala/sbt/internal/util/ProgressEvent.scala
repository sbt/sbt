/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
/** used by super shell */
final class ProgressEvent private (
  val level: String,
  val items: Vector[sbt.internal.util.ProgressItem],
  val lastTaskCount: Option[Int],
  channelName: Option[String],
  execId: Option[String],
  val command: Option[String],
  val skipIfActive: Option[Boolean]) extends sbt.internal.util.AbstractEntry(channelName, execId) with Serializable {
  
  private def this(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Option[Int], channelName: Option[String], execId: Option[String]) = this(level, items, lastTaskCount, channelName, execId, None, None)
  
  override def equals(o: Any): Boolean = o match {
    case x: ProgressEvent => (this.level == x.level) && (this.items == x.items) && (this.lastTaskCount == x.lastTaskCount) && (this.channelName == x.channelName) && (this.execId == x.execId) && (this.command == x.command) && (this.skipIfActive == x.skipIfActive)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.util.ProgressEvent".##) + level.##) + items.##) + lastTaskCount.##) + channelName.##) + execId.##) + command.##) + skipIfActive.##)
  }
  override def toString: String = {
    "ProgressEvent(" + level + ", " + items + ", " + lastTaskCount + ", " + channelName + ", " + execId + ", " + command + ", " + skipIfActive + ")"
  }
  private[this] def copy(level: String = level, items: Vector[sbt.internal.util.ProgressItem] = items, lastTaskCount: Option[Int] = lastTaskCount, channelName: Option[String] = channelName, execId: Option[String] = execId, command: Option[String] = command, skipIfActive: Option[Boolean] = skipIfActive): ProgressEvent = {
    new ProgressEvent(level, items, lastTaskCount, channelName, execId, command, skipIfActive)
  }
  def withLevel(level: String): ProgressEvent = {
    copy(level = level)
  }
  def withItems(items: Vector[sbt.internal.util.ProgressItem]): ProgressEvent = {
    copy(items = items)
  }
  def withLastTaskCount(lastTaskCount: Option[Int]): ProgressEvent = {
    copy(lastTaskCount = lastTaskCount)
  }
  def withLastTaskCount(lastTaskCount: Int): ProgressEvent = {
    copy(lastTaskCount = Option(lastTaskCount))
  }
  def withChannelName(channelName: Option[String]): ProgressEvent = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): ProgressEvent = {
    copy(channelName = Option(channelName))
  }
  def withExecId(execId: Option[String]): ProgressEvent = {
    copy(execId = execId)
  }
  def withExecId(execId: String): ProgressEvent = {
    copy(execId = Option(execId))
  }
  def withCommand(command: Option[String]): ProgressEvent = {
    copy(command = command)
  }
  def withCommand(command: String): ProgressEvent = {
    copy(command = Option(command))
  }
  def withSkipIfActive(skipIfActive: Option[Boolean]): ProgressEvent = {
    copy(skipIfActive = skipIfActive)
  }
  def withSkipIfActive(skipIfActive: Boolean): ProgressEvent = {
    copy(skipIfActive = Option(skipIfActive))
  }
}
object ProgressEvent {
  
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Option[Int], channelName: Option[String], execId: Option[String]): ProgressEvent = new ProgressEvent(level, items, lastTaskCount, channelName, execId)
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Int, channelName: String, execId: String): ProgressEvent = new ProgressEvent(level, items, Option(lastTaskCount), Option(channelName), Option(execId))
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Option[Int], channelName: Option[String], execId: Option[String], command: Option[String], skipIfActive: Option[Boolean]): ProgressEvent = new ProgressEvent(level, items, lastTaskCount, channelName, execId, command, skipIfActive)
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Int, channelName: String, execId: String, command: String, skipIfActive: Boolean): ProgressEvent = new ProgressEvent(level, items, Option(lastTaskCount), Option(channelName), Option(execId), Option(command), Option(skipIfActive))
}
