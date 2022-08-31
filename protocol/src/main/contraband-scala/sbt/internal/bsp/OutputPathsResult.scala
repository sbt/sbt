/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Build Target OutputPaths response */
final class OutputPathsResult private (
  val items: Vector[sbt.internal.bsp.OutputPathsItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: OutputPathsResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.OutputPathsResult".##) + items.##)
  }
  override def toString: String = {
    "OutputPathsResult(" + items + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.OutputPathsItem] = items): OutputPathsResult = {
    new OutputPathsResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.OutputPathsItem]): OutputPathsResult = {
    copy(items = items)
  }
}
object OutputPathsResult {
  
  def apply(items: Vector[sbt.internal.bsp.OutputPathsItem]): OutputPathsResult = new OutputPathsResult(items)
}
