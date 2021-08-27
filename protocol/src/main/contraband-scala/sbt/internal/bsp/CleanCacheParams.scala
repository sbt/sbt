/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Clean Cache Request
 * @param targets A sequence of build targets to clean
 */
final class CleanCacheParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CleanCacheParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.CleanCacheParams".##) + targets.##)
  }
  override def toString: String = {
    "CleanCacheParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): CleanCacheParams = {
    new CleanCacheParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): CleanCacheParams = {
    copy(targets = targets)
  }
}
object CleanCacheParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): CleanCacheParams = new CleanCacheParams(targets)
}
