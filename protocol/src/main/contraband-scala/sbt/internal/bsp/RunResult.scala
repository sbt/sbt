/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Run Result
 * @param originId An optional request id to know the origin of this report.
 * @param statusCode A status code for the execution.
 */
final class RunResult private (
  val originId: Option[String],
  val statusCode: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: RunResult => (this.originId == x.originId) && (this.statusCode == x.statusCode)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.RunResult".##) + originId.##) + statusCode.##)
  }
  override def toString: String = {
    "RunResult(" + originId + ", " + statusCode + ")"
  }
  private[this] def copy(originId: Option[String] = originId, statusCode: Int = statusCode): RunResult = {
    new RunResult(originId, statusCode)
  }
  def withOriginId(originId: Option[String]): RunResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): RunResult = {
    copy(originId = Option(originId))
  }
  def withStatusCode(statusCode: Int): RunResult = {
    copy(statusCode = statusCode)
  }
}
object RunResult {
  
  def apply(originId: Option[String], statusCode: Int): RunResult = new RunResult(originId, statusCode)
  def apply(originId: String, statusCode: Int): RunResult = new RunResult(Option(originId), statusCode)
}
