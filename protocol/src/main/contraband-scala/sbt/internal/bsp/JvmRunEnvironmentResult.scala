/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JvmRunEnvironmentResult private (
  val items: Vector[sbt.internal.bsp.JvmEnvironmentItem],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmRunEnvironmentResult => (this.items == x.items) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmRunEnvironmentResult".##) + items.##) + originId.##)
  }
  override def toString: String = {
    "JvmRunEnvironmentResult(" + items + ", " + originId + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.JvmEnvironmentItem] = items, originId: Option[String] = originId): JvmRunEnvironmentResult = {
    new JvmRunEnvironmentResult(items, originId)
  }
  def withItems(items: Vector[sbt.internal.bsp.JvmEnvironmentItem]): JvmRunEnvironmentResult = {
    copy(items = items)
  }
  def withOriginId(originId: Option[String]): JvmRunEnvironmentResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): JvmRunEnvironmentResult = {
    copy(originId = Option(originId))
  }
}
object JvmRunEnvironmentResult {
  
  def apply(items: Vector[sbt.internal.bsp.JvmEnvironmentItem], originId: Option[String]): JvmRunEnvironmentResult = new JvmRunEnvironmentResult(items, originId)
  def apply(items: Vector[sbt.internal.bsp.JvmEnvironmentItem], originId: String): JvmRunEnvironmentResult = new JvmRunEnvironmentResult(items, Option(originId))
}
