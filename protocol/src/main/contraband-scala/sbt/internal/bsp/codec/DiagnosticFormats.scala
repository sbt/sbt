/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DiagnosticFormats { self: sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.DiagnosticRelatedInformationFormats & sbt.internal.bsp.codec.LocationFormats & sbt.internal.bsp.codec.ScalaDiagnosticFormats & sbt.internal.bsp.codec.ScalaActionFormats & sbt.internal.bsp.codec.ScalaWorkspaceEditFormats & sbt.internal.bsp.codec.ScalaTextEditFormats =>
implicit lazy val DiagnosticFormat: JsonFormat[sbt.internal.bsp.Diagnostic] = new JsonFormat[sbt.internal.bsp.Diagnostic] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.Diagnostic = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val range = unbuilder.readField[sbt.internal.bsp.Range]("range")
      val severity = unbuilder.readField[Option[Long]]("severity")
      val code = unbuilder.readField[Option[String]]("code")
      val source = unbuilder.readField[Option[String]]("source")
      val message = unbuilder.readField[String]("message")
      val relatedInformation = unbuilder.readField[Vector[sbt.internal.bsp.DiagnosticRelatedInformation]]("relatedInformation")
      val dataKind = unbuilder.readField[Option[String]]("dataKind")
      val data = unbuilder.readField[Option[sbt.internal.bsp.ScalaDiagnostic]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.Diagnostic(range, severity, code, source, message, relatedInformation, dataKind, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.Diagnostic, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("range", obj.range)
    builder.addField("severity", obj.severity)
    builder.addField("code", obj.code)
    builder.addField("source", obj.source)
    builder.addField("message", obj.message)
    builder.addField("relatedInformation", obj.relatedInformation)
    builder.addField("dataKind", obj.dataKind)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
