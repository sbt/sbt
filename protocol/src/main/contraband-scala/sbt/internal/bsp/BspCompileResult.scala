/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Compile Response
 * @param originId An optional request id to know the origin of this report.
 * @param statusCode A status code for the execution.
 */
final class BspCompileResult private (
  val originId: Option[String],
  val statusCode: Int) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BspCompileResult => (this.originId == x.originId) && (this.statusCode == x.statusCode)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.BspCompileResult".##) + originId.##) + statusCode.##)
  }
  override def toString: String = {
    "BspCompileResult(" + originId + ", " + statusCode + ")"
  }
  private[this] def copy(originId: Option[String] = originId, statusCode: Int = statusCode): BspCompileResult = {
    new BspCompileResult(originId, statusCode)
  }
  def withOriginId(originId: Option[String]): BspCompileResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): BspCompileResult = {
    copy(originId = Option(originId))
  }
  def withStatusCode(statusCode: Int): BspCompileResult = {
    copy(statusCode = statusCode)
  }
}
object BspCompileResult {
  
  def apply(originId: Option[String], statusCode: Int): BspCompileResult = new BspCompileResult(originId, statusCode)
  def apply(originId: String, statusCode: Int): BspCompileResult = new BspCompileResult(Option(originId), statusCode)
}
