/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing
final class TestStringEvent private (
  val value: String) extends sbt.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TestStringEvent => (this.value == x.value)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.protocol.testing.TestStringEvent".##) + value.##)
  }
  override def toString: String = {
    value
  }
  protected[this] def copy(value: String = value): TestStringEvent = {
    new TestStringEvent(value)
  }
  def withValue(value: String): TestStringEvent = {
    copy(value = value)
  }
}
object TestStringEvent {
  
  def apply(value: String): TestStringEvent = new TestStringEvent(value)
}
