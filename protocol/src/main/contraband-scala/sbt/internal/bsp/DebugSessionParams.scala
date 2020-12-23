/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param targets A sequence of build targets affected by the debugging action.
 * @param dataKind The kind of data to expect in the `data` field.
 * @param data A language-agnostic JSON object interpreted by the server.
 */
final class DebugSessionParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier],
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: DebugSessionParams => (this.targets == x.targets) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.DebugSessionParams".##) + targets.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "DebugSessionParams(" + targets + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): DebugSessionParams = {
    new DebugSessionParams(targets, dataKind, data)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): DebugSessionParams = {
    copy(targets = targets)
  }
  def withDataKind(dataKind: Option[String]): DebugSessionParams = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): DebugSessionParams = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): DebugSessionParams = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): DebugSessionParams = {
    copy(data = Option(data))
  }
}
object DebugSessionParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): DebugSessionParams = new DebugSessionParams(targets, dataKind, data)
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier], dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): DebugSessionParams = new DebugSessionParams(targets, Option(dataKind), Option(data))
}
