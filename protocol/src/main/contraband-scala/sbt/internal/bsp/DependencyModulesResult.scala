/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Dependency Modules Result */
final class DependencyModulesResult private (
  val items: Vector[sbt.internal.bsp.DependencyModulesItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DependencyModulesResult => (this.items == x.items)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.DependencyModulesResult".##) + items.##)
  }
  override def toString: String = {
    "DependencyModulesResult(" + items + ")"
  }
  private def copy(items: Vector[sbt.internal.bsp.DependencyModulesItem]): DependencyModulesResult = {
    new DependencyModulesResult(items)
  }
  def withItems(items: Vector[sbt.internal.bsp.DependencyModulesItem]): DependencyModulesResult = {
    copy(items = items)
  }
}
object DependencyModulesResult {
  
  def apply(items: Vector[sbt.internal.bsp.DependencyModulesItem]): DependencyModulesResult = new DependencyModulesResult(items)
}
