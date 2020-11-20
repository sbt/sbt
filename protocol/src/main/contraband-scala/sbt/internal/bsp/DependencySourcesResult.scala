/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Dependency Sources Result */
final class DependencySourcesResult private (
  val items: Vector[sbt.internal.bsp.DependencySourcesItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: DependencySourcesResult => (this.items == x.items)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.DependencySourcesResult".##) + items.##)
  }
  override def toString: String = {
    "DependencySourcesResult(" + items + ")"
  }
  private[this] def copy(items: Vector[sbt.internal.bsp.DependencySourcesItem] = items): DependencySourcesResult = {
    new DependencySourcesResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.DependencySourcesItem]): DependencySourcesResult = {
    copy(items = items)
  }
}
object DependencySourcesResult {
  
  def apply(items: Vector[sbt.internal.bsp.DependencySourcesItem]): DependencySourcesResult = new DependencySourcesResult(items)
}
