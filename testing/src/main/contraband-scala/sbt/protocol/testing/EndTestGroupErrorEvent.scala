/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
/** Called if test completed with an error. */
final class EndTestGroupErrorEvent private (
  val name: String,
  val error: String) extends sbt.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: EndTestGroupErrorEvent => (this.name == x.name) && (this.error == x.error)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.testing.EndTestGroupErrorEvent".##) + name.##) + error.##)
  }
  override def toString: String = {
    "EndTestGroupErrorEvent(" + name + ", " + error + ")"
  }
  protected[this] def copy(name: String = name, error: String = error): EndTestGroupErrorEvent = {
    new EndTestGroupErrorEvent(name, error)
  }
  def withName(name: String): EndTestGroupErrorEvent = {
    copy(name = name)
  }
  def withError(error: String): EndTestGroupErrorEvent = {
    copy(error = error)
  }
}
object EndTestGroupErrorEvent {
  
  def apply(name: String, error: String): EndTestGroupErrorEvent = new EndTestGroupErrorEvent(name, error)
}
