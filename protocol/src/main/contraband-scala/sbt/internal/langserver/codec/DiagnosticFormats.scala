/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DiagnosticFormats { self: sbt.internal.langserver.codec.RangeFormats & sbt.internal.langserver.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val DiagnosticFormat: JsonFormat[sbt.internal.langserver.Diagnostic] = new JsonFormat[sbt.internal.langserver.Diagnostic] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Diagnostic = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val range = unbuilder.readField[sbt.internal.langserver.Range]("range")
      val severity = unbuilder.readField[Option[Long]]("severity")
      val code = unbuilder.readField[Option[String]]("code")
      val source = unbuilder.readField[Option[String]]("source")
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.internal.langserver.Diagnostic(range, severity, code, source, message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Diagnostic, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("range", obj.range)
    builder.addField("severity", obj.severity)
    builder.addField("code", obj.code)
    builder.addField("source", obj.source)
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
