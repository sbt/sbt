/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompileReportFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val CompileReportFormat: JsonFormat[sbt.internal.bsp.CompileReport] = new JsonFormat[sbt.internal.bsp.CompileReport] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.CompileReport = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val originId = unbuilder.readField[Option[String]]("originId")
      val errors = unbuilder.readField[Int]("errors")
      val warnings = unbuilder.readField[Int]("warnings")
      val time = unbuilder.readField[Option[Int]]("time")
      val noOp = unbuilder.readField[Option[Boolean]]("noOp")
      unbuilder.endObject()
      sbt.internal.bsp.CompileReport(target, originId, errors, warnings, time, noOp)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.CompileReport, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("originId", obj.originId)
    builder.addField("errors", obj.errors)
    builder.addField("warnings", obj.warnings)
    builder.addField("time", obj.time)
    builder.addField("noOp", obj.noOp)
    builder.endObject()
  }
}
}
