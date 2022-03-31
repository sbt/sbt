/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JvmRunEnvironmentParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmRunEnvironmentParams => (this.targets == x.targets) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmRunEnvironmentParams".##) + targets.##) + originId.##)
  }
  override def toString: String = {
    "JvmRunEnvironmentParams(" + targets + ", " + originId + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets, originId: Option[String] = originId): JvmRunEnvironmentParams = {
    new JvmRunEnvironmentParams(targets, originId)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): JvmRunEnvironmentParams = {
    copy(targets = targets)
  }
  def withOriginId(originId: Option[String]): JvmRunEnvironmentParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): JvmRunEnvironmentParams = {
    copy(originId = Option(originId))
  }
}
object JvmRunEnvironmentParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: Option[String]): JvmRunEnvironmentParams = new JvmRunEnvironmentParams(targets, originId)
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: String): JvmRunEnvironmentParams = new JvmRunEnvironmentParams(targets, Option(originId))
}
