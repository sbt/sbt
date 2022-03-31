/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JvmTestEnvironmentResult private (
  val items: Vector[sbt.internal.bsp.JvmEnvironmentItem],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmTestEnvironmentResult => (this.items == x.items) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmTestEnvironmentResult".##) + items.##) + originId.##)
  }
  override def toString: String = {
    "JvmTestEnvironmentResult(" + items + ", " + originId + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.JvmEnvironmentItem] = items, originId: Option[String] = originId): JvmTestEnvironmentResult = {
    new JvmTestEnvironmentResult(items, originId)
  }
  def withItems(items: Vector[sbt.internal.bsp.JvmEnvironmentItem]): JvmTestEnvironmentResult = {
    copy(items = items)
  }
  def withOriginId(originId: Option[String]): JvmTestEnvironmentResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): JvmTestEnvironmentResult = {
    copy(originId = Option(originId))
  }
}
object JvmTestEnvironmentResult {
  
  def apply(items: Vector[sbt.internal.bsp.JvmEnvironmentItem], originId: Option[String]): JvmTestEnvironmentResult = new JvmTestEnvironmentResult(items, originId)
  def apply(items: Vector[sbt.internal.bsp.JvmEnvironmentItem], originId: String): JvmTestEnvironmentResult = new JvmTestEnvironmentResult(items, Option(originId))
}
