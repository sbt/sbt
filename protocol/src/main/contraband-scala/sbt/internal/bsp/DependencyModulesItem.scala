/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class DependencyModulesItem private (
  val target: Option[sbt.internal.bsp.BuildTargetIdentifier],
  val modules: Vector[sbt.internal.bsp.DependencyModule]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DependencyModulesItem => (this.target == x.target) && (this.modules == x.modules)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.DependencyModulesItem".##) + target.##) + modules.##)
  }
  override def toString: String = {
    "DependencyModulesItem(" + target + ", " + modules + ")"
  }
  private def copy(target: Option[sbt.internal.bsp.BuildTargetIdentifier] = target, modules: Vector[sbt.internal.bsp.DependencyModule] = modules): DependencyModulesItem = {
    new DependencyModulesItem(target, modules)
  }
  def withTarget(target: Option[sbt.internal.bsp.BuildTargetIdentifier]): DependencyModulesItem = {
    copy(target = target)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): DependencyModulesItem = {
    copy(target = Option(target))
  }
  def withModules(modules: Vector[sbt.internal.bsp.DependencyModule]): DependencyModulesItem = {
    copy(modules = modules)
  }
}
object DependencyModulesItem {
  
  def apply(target: Option[sbt.internal.bsp.BuildTargetIdentifier], modules: Vector[sbt.internal.bsp.DependencyModule]): DependencyModulesItem = new DependencyModulesItem(target, modules)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, modules: Vector[sbt.internal.bsp.DependencyModule]): DependencyModulesItem = new DependencyModulesItem(Option(target), modules)
}
