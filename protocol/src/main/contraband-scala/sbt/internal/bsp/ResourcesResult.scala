/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Build Target Resources response */
final class ResourcesResult private (
  val items: Vector[sbt.internal.bsp.ResourcesItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ResourcesResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ResourcesResult".##) + items.##)
  }
  override def toString: String = {
    "ResourcesResult(" + items + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.ResourcesItem] = items): ResourcesResult = {
    new ResourcesResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.ResourcesItem]): ResourcesResult = {
    copy(items = items)
  }
}
object ResourcesResult {
  
  def apply(items: Vector[sbt.internal.bsp.ResourcesItem]): ResourcesResult = new ResourcesResult(items)
}
