/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Status event. */
final class StatusEvent private (
  val status: String,
  val commandQueue: Vector[String]) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: StatusEvent => (this.status == x.status) && (this.commandQueue == x.commandQueue)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + status.##) + commandQueue.##)
  }
  override def toString: String = {
    "StatusEvent(" + status + ", " + commandQueue + ")"
  }
  protected[this] def copy(status: String = status, commandQueue: Vector[String] = commandQueue): StatusEvent = {
    new StatusEvent(status, commandQueue)
  }
  def withStatus(status: String): StatusEvent = {
    copy(status = status)
  }
  def withCommandQueue(commandQueue: Vector[String]): StatusEvent = {
    copy(commandQueue = commandQueue)
  }
}
object StatusEvent {
  
  def apply(status: String, commandQueue: Vector[String]): StatusEvent = new StatusEvent(status, commandQueue)
}
