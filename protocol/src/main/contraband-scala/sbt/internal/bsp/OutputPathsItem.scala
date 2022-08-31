/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class OutputPathsItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val outputPaths: Vector[sbt.internal.bsp.OutputPathItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: OutputPathsItem => (this.target == x.target) && (this.outputPaths == x.outputPaths)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.OutputPathsItem".##) + target.##) + outputPaths.##)
  }
  override def toString: String = {
    "OutputPathsItem(" + target + ", " + outputPaths + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, outputPaths: Vector[sbt.internal.bsp.OutputPathItem] = outputPaths): OutputPathsItem = {
    new OutputPathsItem(target, outputPaths)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): OutputPathsItem = {
    copy(target = target)
  }
  def withOutputPaths(outputPaths: Vector[sbt.internal.bsp.OutputPathItem]): OutputPathsItem = {
    copy(outputPaths = outputPaths)
  }
}
object OutputPathsItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, outputPaths: Vector[sbt.internal.bsp.OutputPathItem]): OutputPathsItem = new OutputPathsItem(target, outputPaths)
}
