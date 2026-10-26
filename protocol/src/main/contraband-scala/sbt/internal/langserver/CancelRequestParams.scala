/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Id for a cancel request */
final class CancelRequestParams private (
  val id: String,
  val channelName: Option[String]) extends Serializable {
  
  private def this(id: String) = this(id, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CancelRequestParams => (this.id == x.id) && (this.channelName == x.channelName)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.CancelRequestParams".##) + id.##) + channelName.##)
  }
  override def toString: String = {
    "CancelRequestParams(" + id + ", " + channelName + ")"
  }
  private def copy(id: String = id, channelName: Option[String] = channelName): CancelRequestParams = {
    new CancelRequestParams(id, channelName)
  }
  def withId(id: String): CancelRequestParams = {
    copy(id = id)
  }
  def withChannelName(channelName: Option[String]): CancelRequestParams = {
    copy(channelName = channelName)
  }
  def withChannelName(channelName: String): CancelRequestParams = {
    copy(channelName = Option(channelName))
  }
}
object CancelRequestParams {
  
  def apply(id: String): CancelRequestParams = new CancelRequestParams(id)
  def apply(id: String, channelName: Option[String]): CancelRequestParams = new CancelRequestParams(id, channelName)
  def apply(id: String, channelName: String): CancelRequestParams = new CancelRequestParams(id, Option(channelName))
}
