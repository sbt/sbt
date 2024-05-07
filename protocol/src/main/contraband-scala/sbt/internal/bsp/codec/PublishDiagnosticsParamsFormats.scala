/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PublishDiagnosticsParamsFormats { self: sbt.internal.bsp.codec.TextDocumentIdentifierFormats with sjsonnew.BasicJsonProtocol with sbt.internal.bsp.codec.BuildTargetIdentifierFormats with sbt.internal.bsp.codec.DiagnosticFormats with sbt.internal.bsp.codec.RangeFormats with sbt.internal.bsp.codec.PositionFormats with sbt.internal.bsp.codec.DiagnosticRelatedInformationFormats with sbt.internal.bsp.codec.LocationFormats with sbt.internal.bsp.codec.ScalaDiagnosticFormats with sbt.internal.bsp.codec.ScalaActionFormats with sbt.internal.bsp.codec.ScalaWorkspaceEditFormats with sbt.internal.bsp.codec.ScalaTextEditFormats =>
implicit lazy val PublishDiagnosticsParamsFormat: JsonFormat[sbt.internal.bsp.PublishDiagnosticsParams] = new JsonFormat[sbt.internal.bsp.PublishDiagnosticsParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.PublishDiagnosticsParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val textDocument = unbuilder.readField[sbt.internal.bsp.TextDocumentIdentifier]("textDocument")
      val buildTarget = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("buildTarget")
      val originId = unbuilder.readField[Option[String]]("originId")
      val diagnostics = unbuilder.readField[Vector[sbt.internal.bsp.Diagnostic]]("diagnostics")
      val reset = unbuilder.readField[Boolean]("reset")
      unbuilder.endObject()
      sbt.internal.bsp.PublishDiagnosticsParams(textDocument, buildTarget, originId, diagnostics, reset)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.PublishDiagnosticsParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("textDocument", obj.textDocument)
    builder.addField("buildTarget", obj.buildTarget)
    builder.addField("originId", obj.originId)
    builder.addField("diagnostics", obj.diagnostics)
    builder.addField("reset", obj.reset)
    builder.endObject()
  }
}
}
