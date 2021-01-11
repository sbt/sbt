/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class ScalacOptionsResult private (
  val items: Vector[sbt.internal.bsp.ScalacOptionsItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalacOptionsResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ScalacOptionsResult".##) + items.##)
  }
  override def toString: String = {
    "ScalacOptionsResult(" + items + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.ScalacOptionsItem] = items): ScalacOptionsResult = {
    new ScalacOptionsResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.ScalacOptionsItem]): ScalacOptionsResult = {
    copy(items = items)
  }
}
object ScalacOptionsResult {
  
  def apply(items: Vector[sbt.internal.bsp.ScalacOptionsItem]): ScalacOptionsResult = new ScalacOptionsResult(items)
}
