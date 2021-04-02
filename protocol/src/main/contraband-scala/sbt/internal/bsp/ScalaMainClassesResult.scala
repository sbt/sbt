/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param originId An optional id of the request that triggered this result. */
final class ScalaMainClassesResult private (
  val items: Vector[sbt.internal.bsp.ScalaMainClassesItem],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaMainClassesResult => (this.items == x.items) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaMainClassesResult".##) + items.##) + originId.##)
  }
  override def toString: String = {
    "ScalaMainClassesResult(" + items + ", " + originId + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.ScalaMainClassesItem] = items, originId: Option[String] = originId): ScalaMainClassesResult = {
    new ScalaMainClassesResult(items, originId)
  }
  def withItems(items: Vector[sbt.internal.bsp.ScalaMainClassesItem]): ScalaMainClassesResult = {
    copy(items = items)
  }
  def withOriginId(originId: Option[String]): ScalaMainClassesResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): ScalaMainClassesResult = {
    copy(originId = Option(originId))
  }
}
object ScalaMainClassesResult {
  
  def apply(items: Vector[sbt.internal.bsp.ScalaMainClassesItem], originId: Option[String]): ScalaMainClassesResult = new ScalaMainClassesResult(items, originId)
  def apply(items: Vector[sbt.internal.bsp.ScalaMainClassesItem], originId: String): ScalaMainClassesResult = new ScalaMainClassesResult(items, Option(originId))
}
