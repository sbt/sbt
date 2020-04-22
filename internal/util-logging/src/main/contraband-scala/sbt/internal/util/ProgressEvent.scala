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
  execId: Option[String]) extends sbt.internal.util.AbstractEntry(channelName, execId) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ProgressEvent => (this.level == x.level) && (this.items == x.items) && (this.lastTaskCount == x.lastTaskCount) && (this.channelName == x.channelName) && (this.execId == x.execId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.util.ProgressEvent".##) + level.##) + items.##) + lastTaskCount.##) + channelName.##) + execId.##)
  }
  override def toString: String = {
    "ProgressEvent(" + level + ", " + items + ", " + lastTaskCount + ", " + channelName + ", " + execId + ")"
  }
  private[this] def copy(level: String = level, items: Vector[sbt.internal.util.ProgressItem] = items, lastTaskCount: Option[Int] = lastTaskCount, channelName: Option[String] = channelName, execId: Option[String] = execId): ProgressEvent = {
    new ProgressEvent(level, items, lastTaskCount, channelName, execId)
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
}
object ProgressEvent {
  
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Option[Int], channelName: Option[String], execId: Option[String]): ProgressEvent = new ProgressEvent(level, items, lastTaskCount, channelName, execId)
  def apply(level: String, items: Vector[sbt.internal.util.ProgressItem], lastTaskCount: Int, channelName: String, execId: String): ProgressEvent = new ProgressEvent(level, items, Option(lastTaskCount), Option(channelName), Option(execId))
}
