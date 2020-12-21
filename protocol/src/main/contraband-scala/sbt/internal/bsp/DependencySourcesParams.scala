/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Dependency Sources Request */
final class DependencySourcesParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: DependencySourcesParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.DependencySourcesParams".##) + targets.##)
  }
  override def toString: String = {
    "DependencySourcesParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): DependencySourcesParams = {
    new DependencySourcesParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): DependencySourcesParams = {
    copy(targets = targets)
  }
}
object DependencySourcesParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): DependencySourcesParams = new DependencySourcesParams(targets)
}
