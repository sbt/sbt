/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ServerCapabilitiesFormats { self: sbt.internal.langserver.codec.TextDocumentSyncOptionsFormats & sbt.internal.langserver.codec.SaveOptionsFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ServerCapabilitiesFormat: JsonFormat[sbt.internal.langserver.ServerCapabilities] = new JsonFormat[sbt.internal.langserver.ServerCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.ServerCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val textDocumentSync = unbuilder.readField[Option[sbt.internal.langserver.TextDocumentSyncOptions]]("textDocumentSync")
      val hoverProvider = unbuilder.readField[Option[Boolean]]("hoverProvider")
      val definitionProvider = unbuilder.readField[Option[Boolean]]("definitionProvider")
      unbuilder.endObject()
      sbt.internal.langserver.ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.ServerCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("textDocumentSync", obj.textDocumentSync)
    builder.addField("hoverProvider", obj.hoverProvider)
    builder.addField("definitionProvider", obj.definitionProvider)
    builder.endObject()
  }
}
}
