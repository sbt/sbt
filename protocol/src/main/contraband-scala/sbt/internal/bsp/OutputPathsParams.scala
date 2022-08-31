/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Build Target OutputPaths Request */
final class OutputPathsParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: OutputPathsParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.OutputPathsParams".##) + targets.##)
  }
  override def toString: String = {
    "OutputPathsParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): OutputPathsParams = {
    new OutputPathsParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): OutputPathsParams = {
    copy(targets = targets)
  }
}
object OutputPathsParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): OutputPathsParams = new OutputPathsParams(targets)
}
