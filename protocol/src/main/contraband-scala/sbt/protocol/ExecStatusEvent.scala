/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Status event. */
final class ExecStatusEvent private (
  val status: String,
  val commandQueue: Vector[String]) extends sbt.protocol.EventMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecStatusEvent => (this.status == x.status) && (this.commandQueue == x.commandQueue)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + status.##) + commandQueue.##)
  }
  override def toString: String = {
    "ExecStatusEvent(" + status + ", " + commandQueue + ")"
  }
  protected[this] def copy(status: String = status, commandQueue: Vector[String] = commandQueue): ExecStatusEvent = {
    new ExecStatusEvent(status, commandQueue)
  }
  def withStatus(status: String): ExecStatusEvent = {
    copy(status = status)
  }
  def withCommandQueue(commandQueue: Vector[String]): ExecStatusEvent = {
    copy(commandQueue = commandQueue)
  }
}
object ExecStatusEvent {
  
  def apply(status: String, commandQueue: Vector[String]): ExecStatusEvent = new ExecStatusEvent(status, commandQueue)
}
