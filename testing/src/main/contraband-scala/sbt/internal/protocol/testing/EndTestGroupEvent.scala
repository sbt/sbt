/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing
/** Called if test completed. */
final class EndTestGroupEvent private (
  val name: String,
  val result: sbt.internal.protocol.testing.TestResult) extends sbt.internal.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: EndTestGroupEvent => (this.name == x.name) && (this.result == x.result)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.testing.EndTestGroupEvent".##) + name.##) + result.##)
  }
  override def toString: String = {
    "EndTestGroupEvent(" + name + ", " + result + ")"
  }
  protected[this] def copy(name: String = name, result: sbt.internal.protocol.testing.TestResult = result): EndTestGroupEvent = {
    new EndTestGroupEvent(name, result)
  }
  def withName(name: String): EndTestGroupEvent = {
    copy(name = name)
  }
  def withResult(result: sbt.internal.protocol.testing.TestResult): EndTestGroupEvent = {
    copy(result = result)
  }
}
object EndTestGroupEvent {
  
  def apply(name: String, result: sbt.internal.protocol.testing.TestResult): EndTestGroupEvent = new EndTestGroupEvent(name, result)
}
