/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing
/** Called once, at end of the testing. */
final class TestCompleteEvent private (
  val result: sbt.internal.protocol.testing.TestResult) extends sbt.internal.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TestCompleteEvent => (this.result == x.result)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.protocol.testing.TestCompleteEvent".##) + result.##)
  }
  override def toString: String = {
    "TestCompleteEvent(" + result + ")"
  }
  protected[this] def copy(result: sbt.internal.protocol.testing.TestResult = result): TestCompleteEvent = {
    new TestCompleteEvent(result)
  }
  def withResult(result: sbt.internal.protocol.testing.TestResult): TestCompleteEvent = {
    copy(result = result)
  }
}
object TestCompleteEvent {
  
  def apply(result: sbt.internal.protocol.testing.TestResult): TestCompleteEvent = new TestCompleteEvent(result)
}
