/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PublishDiagnosticsParamsFormats { self: sbt.internal.langserver.codec.DiagnosticFormats & sbt.internal.langserver.codec.RangeFormats & sbt.internal.langserver.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val PublishDiagnosticsParamsFormat: JsonFormat[sbt.internal.langserver.PublishDiagnosticsParams] = new JsonFormat[sbt.internal.langserver.PublishDiagnosticsParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.PublishDiagnosticsParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[String]("uri")
      val diagnostics = unbuilder.readField[Vector[sbt.internal.langserver.Diagnostic]]("diagnostics")
      unbuilder.endObject()
      sbt.internal.langserver.PublishDiagnosticsParams(uri, diagnostics)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.PublishDiagnosticsParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("diagnostics", obj.diagnostics)
    builder.endObject()
  }
}
}
