/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Test Result
 * @param originId An optional request id to know the origin of this report.
 * @param statusCode A status code for the execution.
 */
final class TestResult private (
  val originId: Option[String],
  val statusCode: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TestResult => (this.originId == x.originId) && (this.statusCode == x.statusCode)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.TestResult".##) + originId.##) + statusCode.##)
  }
  override def toString: String = {
    "TestResult(" + originId + ", " + statusCode + ")"
  }
  private[this] def copy(originId: Option[String] = originId, statusCode: Int = statusCode): TestResult = {
    new TestResult(originId, statusCode)
  }
  def withOriginId(originId: Option[String]): TestResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): TestResult = {
    copy(originId = Option(originId))
  }
  def withStatusCode(statusCode: Int): TestResult = {
    copy(statusCode = statusCode)
  }
}
object TestResult {
  
  def apply(originId: Option[String], statusCode: Int): TestResult = new TestResult(originId, statusCode)
  def apply(originId: String, statusCode: Int): TestResult = new TestResult(Option(originId), statusCode)
}
