/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Scalac options
 * The build target scalac options request is sent from the client to the server
 * to query for the list of compiler options necessary to compile in a given list of targets.
 */
final class ScalacOptionsParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalacOptionsParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.ScalacOptionsParams".##) + targets.##)
  }
  override def toString: String = {
    "ScalacOptionsParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): ScalacOptionsParams = {
    new ScalacOptionsParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ScalacOptionsParams = {
    copy(targets = targets)
  }
}
object ScalacOptionsParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ScalacOptionsParams = new ScalacOptionsParams(targets)
}
