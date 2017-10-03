/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ServerCapabilitiesFormats { self: sbt.internal.langserver.codec.TextDocumentSyncOptionsFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ServerCapabilitiesFormat: JsonFormat[sbt.internal.langserver.ServerCapabilities] = new JsonFormat[sbt.internal.langserver.ServerCapabilities] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.ServerCapabilities = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val textDocumentSync = unbuilder.readField[Option[sbt.internal.langserver.TextDocumentSyncOptions]]("textDocumentSync")
      val hoverProvider = unbuilder.readField[Option[Boolean]]("hoverProvider")
      unbuilder.endObject()
      sbt.internal.langserver.ServerCapabilities(textDocumentSync, hoverProvider)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.ServerCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("textDocumentSync", obj.textDocumentSync)
    builder.addField("hoverProvider", obj.hoverProvider)
    builder.endObject()
  }
}
}
