/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param originId An optional id of the request that triggered this result. */
final class ScalaTestClassesResult private (
  val items: Vector[sbt.internal.bsp.ScalaTestClassesItem],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ScalaTestClassesResult => (this.items == x.items) && (this.originId == x.originId)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaTestClassesResult".##) + items.##) + originId.##)
  }
  override def toString: String = {
    "ScalaTestClassesResult(" + items + ", " + originId + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.ScalaTestClassesItem] = items, originId: Option[String] = originId): ScalaTestClassesResult = {
    new ScalaTestClassesResult(items, originId)
  }
  def withItems(items: Vector[sbt.internal.bsp.ScalaTestClassesItem]): ScalaTestClassesResult = {
    copy(items = items)
  }
  def withOriginId(originId: Option[String]): ScalaTestClassesResult = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): ScalaTestClassesResult = {
    copy(originId = Option(originId))
  }
}
object ScalaTestClassesResult {
  
  def apply(items: Vector[sbt.internal.bsp.ScalaTestClassesItem], originId: Option[String]): ScalaTestClassesResult = new ScalaTestClassesResult(items, originId)
  def apply(items: Vector[sbt.internal.bsp.ScalaTestClassesItem], originId: String): ScalaTestClassesResult = new ScalaTestClassesResult(items, Option(originId))
}
