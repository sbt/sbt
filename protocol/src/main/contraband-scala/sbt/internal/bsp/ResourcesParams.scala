/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class ResourcesParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ResourcesParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ResourcesParams".##) + targets.##)
  }
  override def toString: String = {
    "ResourcesParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): ResourcesParams = {
    new ResourcesParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ResourcesParams = {
    copy(targets = targets)
  }
}
object ResourcesParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ResourcesParams = new ResourcesParams(targets)
}
