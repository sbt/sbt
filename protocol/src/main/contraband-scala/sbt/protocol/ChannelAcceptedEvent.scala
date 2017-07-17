/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
final class ChannelAcceptedEvent private (
  val channelName: String) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ChannelAcceptedEvent => (this.channelName == x.channelName)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.ChannelAcceptedEvent".##) + channelName.##)
  }
  override def toString: String = {
    "ChannelAcceptedEvent(" + channelName + ")"
  }
  protected[this] def copy(channelName: String = channelName): ChannelAcceptedEvent = {
    new ChannelAcceptedEvent(channelName)
  }
  def withChannelName(channelName: String): ChannelAcceptedEvent = {
    copy(channelName = channelName)
  }
}
object ChannelAcceptedEvent {
  
  def apply(channelName: String): ChannelAcceptedEvent = new ChannelAcceptedEvent(channelName)
}
