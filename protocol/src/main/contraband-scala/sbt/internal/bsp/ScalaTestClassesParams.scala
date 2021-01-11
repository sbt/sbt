/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Scala Test Class Request
 * The build target scala test options request is sent from the client to the server
 * to query for the list of fully qualified names of test clases in a given list of targets.
 * @param originId An optional number uniquely identifying a client request.
 */
final class ScalaTestClassesParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val originId: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaTestClassesParams => (this.targets == x.targets) && (this.originId == x.originId)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaTestClassesParams".##) + targets.##) + originId.##)
  }
  override def toString: String = {
    "ScalaTestClassesParams(" + targets + ", " + originId + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets, originId: Option[String] = originId): ScalaTestClassesParams = {
    new ScalaTestClassesParams(targets, originId)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): ScalaTestClassesParams = {
    copy(targets = targets)
  }
  def withOriginId(originId: Option[String]): ScalaTestClassesParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): ScalaTestClassesParams = {
    copy(originId = Option(originId))
  }
}
object ScalaTestClassesParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: Option[String]): ScalaTestClassesParams = new ScalaTestClassesParams(targets, originId)
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], originId: String): ScalaTestClassesParams = new ScalaTestClassesParams(targets, Option(originId))
}
