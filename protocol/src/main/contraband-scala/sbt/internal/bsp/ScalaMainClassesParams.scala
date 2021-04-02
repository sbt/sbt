/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Scala Main Class Request
 * The build target main classes request is sent from the client to the server
 * to query for the list of main classes that can be fed as arguments to buildTarget/run.
 * @param originId An optional number uniquely identifying a client request.
 */
final class ScalaMainClassesParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaMainClassesParams => (this.targets == x.targets) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaMainClassesParams".##) + targets.##) + originId.##)
  }
  override def toString: String = {
    "ScalaMainClassesParams(" + targets + ", " + originId + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets, originId: Option[String] = originId): ScalaMainClassesParams = {
    new ScalaMainClassesParams(targets, originId)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ScalaMainClassesParams = {
    copy(targets = targets)
  }
  def withOriginId(originId: Option[String]): ScalaMainClassesParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): ScalaMainClassesParams = {
    copy(originId = Option(originId))
  }
}
object ScalaMainClassesParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: Option[String]): ScalaMainClassesParams = new ScalaMainClassesParams(targets, originId)
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: String): ScalaMainClassesParams = new ScalaMainClassesParams(targets, Option(originId))
}
