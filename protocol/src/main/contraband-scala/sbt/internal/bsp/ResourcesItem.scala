/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param resources List of resource files. */
final class ResourcesItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val resources: Vector[java.net.URI]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ResourcesItem => (this.target == x.target) && (this.resources == x.resources)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ResourcesItem".##) + target.##) + resources.##)
  }
  override def toString: String = {
    "ResourcesItem(" + target + ", " + resources + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, resources: Vector[java.net.URI] = resources): ResourcesItem = {
    new ResourcesItem(target, resources)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): ResourcesItem = {
    copy(target = target)
  }
  def withResources(resources: Vector[java.net.URI]): ResourcesItem = {
    copy(resources = resources)
  }
}
object ResourcesItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, resources: Vector[java.net.URI]): ResourcesItem = new ResourcesItem(target, resources)
}
