/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Build Target Sources Request */
final class SourcesParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SourcesParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.SourcesParams".##) + targets.##)
  }
  override def toString: String = {
    "SourcesParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): SourcesParams = {
    new SourcesParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): SourcesParams = {
    copy(targets = targets)
  }
}
object SourcesParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): SourcesParams = new SourcesParams(targets)
}
