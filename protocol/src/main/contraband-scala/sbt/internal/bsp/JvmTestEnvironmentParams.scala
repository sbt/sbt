/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JvmTestEnvironmentParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmTestEnvironmentParams => (this.targets == x.targets) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmTestEnvironmentParams".##) + targets.##) + originId.##)
  }
  override def toString: String = {
    "JvmTestEnvironmentParams(" + targets + ", " + originId + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets, originId: Option[String] = originId): JvmTestEnvironmentParams = {
    new JvmTestEnvironmentParams(targets, originId)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): JvmTestEnvironmentParams = {
    copy(targets = targets)
  }
  def withOriginId(originId: Option[String]): JvmTestEnvironmentParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): JvmTestEnvironmentParams = {
    copy(originId = Option(originId))
  }
}
object JvmTestEnvironmentParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: Option[String]): JvmTestEnvironmentParams = new JvmTestEnvironmentParams(targets, originId)
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: String): JvmTestEnvironmentParams = new JvmTestEnvironmentParams(targets, Option(originId))
}
