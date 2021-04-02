/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Run Request
 * The run request is sent from the client to the server to run a build target.
 * The server communicates during the initialize handshake whether this method is supported or not.
 * An empty run request is valid.
 * @param target The build target to run.
 * @param originId An option identifier gnerated by the client to identify this request.
                   The server may include this id in triggered notifications or responses.
 * @param arguments Optional arguments to the executed application.
 * @param dataKind Kind of data to expect in the data field.
                   If this field is not set, the kind of data is not specified.
 * @param data Language-specific metadata for this execution.
 */
final class RunParams private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val originId: Option[String],
  val arguments: Vector[String],
  val dataKind: Option[String],
  val data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: RunParams => (this.target == x.target) && (this.originId == x.originId) && (this.arguments == x.arguments) && (this.dataKind == x.dataKind) && (this.data == x.data)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.RunParams".##) + target.##) + originId.##) + arguments.##) + dataKind.##) + data.##)
  }
  override def toString: String = {
    "RunParams(" + target + ", " + originId + ", " + arguments + ", " + dataKind + ", " + data + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, originId: Option[String] = originId, arguments: Vector[String] = arguments, dataKind: Option[String] = dataKind, data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue] = data): RunParams = {
    new RunParams(target, originId, arguments, dataKind, data)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): RunParams = {
    copy(target = target)
  }
  def withOriginId(originId: Option[String]): RunParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): RunParams = {
    copy(originId = Option(originId))
  }
  def withArguments(arguments: Vector[String]): RunParams = {
    copy(arguments = arguments)
  }
  def withDataKind(dataKind: Option[String]): RunParams = {
    copy(dataKind = dataKind)
  }
  def withDataKind(dataKind: String): RunParams = {
    copy(dataKind = Option(dataKind))
  }
  def withData(data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): RunParams = {
    copy(data = data)
  }
  def withData(data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): RunParams = {
    copy(data = Option(data))
  }
}
object RunParams {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: Option[String], arguments: Vector[String], dataKind: Option[String], data: Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]): RunParams = new RunParams(target, originId, arguments, dataKind, data)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: String, arguments: Vector[String], dataKind: String, data: sjsonnew.shaded.scalajson.ast.unsafe.JValue): RunParams = new RunParams(target, Option(originId), arguments, Option(dataKind), Option(data))
}
