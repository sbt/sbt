/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
final class CommandSource private (
  val channelName: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CommandSource => (this.channelName == x.channelName)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.CommandSource".##) + channelName.##)
  }
  override def toString: String = {
    "CommandSource(" + channelName + ")"
  }
  private[this] def copy(channelName: String = channelName): CommandSource = {
    new CommandSource(channelName)
  }
  def withChannelName(channelName: String): CommandSource = {
    copy(channelName = channelName)
  }
}
object CommandSource {
  
  def apply(channelName: String): CommandSource = new CommandSource(channelName)
}
