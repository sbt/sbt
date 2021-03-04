/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Id for a cancel request */
final class CancelRequestParams private (
  val id: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CancelRequestParams => (this.id == x.id)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.CancelRequestParams".##) + id.##)
  }
  override def toString: String = {
    "CancelRequestParams(" + id + ")"
  }
  private[this] def copy(id: String = id): CancelRequestParams = {
    new CancelRequestParams(id)
  }
  def withId(id: String): CancelRequestParams = {
    copy(id = id)
  }
}
object CancelRequestParams {
  
  def apply(id: String): CancelRequestParams = new CancelRequestParams(id)
}
