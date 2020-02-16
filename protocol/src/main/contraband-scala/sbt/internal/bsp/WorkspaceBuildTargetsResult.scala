/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Workspace Build Targets response
 * @param targets The build targets in this workspace that
                  contain sources with the given language ids.
 */
final class WorkspaceBuildTargetsResult private (
  val targets: Vector[sbt.internal.bsp.BuildTarget]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: WorkspaceBuildTargetsResult => (this.targets == x.targets)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.WorkspaceBuildTargetsResult".##) + targets.##)
  }
  override def toString: String = {
    "WorkspaceBuildTargetsResult(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTarget] = targets): WorkspaceBuildTargetsResult = {
    new WorkspaceBuildTargetsResult(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTarget]): WorkspaceBuildTargetsResult = {
    copy(targets = targets)
  }
}
object WorkspaceBuildTargetsResult {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTarget]): WorkspaceBuildTargetsResult = new WorkspaceBuildTargetsResult(targets)
}
