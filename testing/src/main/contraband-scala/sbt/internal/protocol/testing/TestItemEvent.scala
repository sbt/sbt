/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing
/** Called for each test method or equivalent. */
final class TestItemEvent private (
  val result: Option[sbt.internal.protocol.testing.TestResult],
  val detail: Vector[sbt.internal.protocol.testing.TestItemDetail]) extends sbt.internal.protocol.testing.TestMessage() with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TestItemEvent => (this.result == x.result) && (this.detail == x.detail)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.protocol.testing.TestItemEvent".##) + result.##) + detail.##)
  }
  override def toString: String = {
    "TestItemEvent(" + result + ", " + detail + ")"
  }
  protected[this] def copy(result: Option[sbt.internal.protocol.testing.TestResult] = result, detail: Vector[sbt.internal.protocol.testing.TestItemDetail] = detail): TestItemEvent = {
    new TestItemEvent(result, detail)
  }
  def withResult(result: Option[sbt.internal.protocol.testing.TestResult]): TestItemEvent = {
    copy(result = result)
  }
  def withResult(result: sbt.internal.protocol.testing.TestResult): TestItemEvent = {
    copy(result = Option(result))
  }
  def withDetail(detail: Vector[sbt.internal.protocol.testing.TestItemDetail]): TestItemEvent = {
    copy(detail = detail)
  }
}
object TestItemEvent {
  
  def apply(result: Option[sbt.internal.protocol.testing.TestResult], detail: Vector[sbt.internal.protocol.testing.TestItemDetail]): TestItemEvent = new TestItemEvent(result, detail)
  def apply(result: sbt.internal.protocol.testing.TestResult, detail: Vector[sbt.internal.protocol.testing.TestItemDetail]): TestItemEvent = new TestItemEvent(Option(result), detail)
}
