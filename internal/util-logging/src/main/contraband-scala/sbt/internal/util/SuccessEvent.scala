/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util
final class SuccessEvent private (
  val message: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SuccessEvent => (this.message == x.message)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.util.SuccessEvent".##) + message.##)
  }
  override def toString: String = {
    "SuccessEvent(" + message + ")"
  }
  private[this] def copy(message: String = message): SuccessEvent = {
    new SuccessEvent(message)
  }
  def withMessage(message: String): SuccessEvent = {
    copy(message = message)
  }
}
object SuccessEvent {
  
  def apply(message: String): SuccessEvent = new SuccessEvent(message)
}
