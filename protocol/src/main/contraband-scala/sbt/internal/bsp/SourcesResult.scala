/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Build Target Sources response */
final class SourcesResult private (
  val items: Vector[sbt.internal.bsp.SourcesItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SourcesResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.SourcesResult".##) + items.##)
  }
  override def toString: String = {
    "SourcesResult(" + items + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.SourcesItem] = items): SourcesResult = {
    new SourcesResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.SourcesItem]): SourcesResult = {
    copy(items = items)
  }
}
object SourcesResult {
  
  def apply(items: Vector[sbt.internal.bsp.SourcesItem]): SourcesResult = new SourcesResult(items)
}
