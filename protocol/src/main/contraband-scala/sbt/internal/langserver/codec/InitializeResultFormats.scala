/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeResultFormats { self: sbt.internal.langserver.codec.ServerCapabilitiesFormats & sbt.internal.langserver.codec.TextDocumentSyncOptionsFormats & sbt.internal.langserver.codec.SaveOptionsFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeResultFormat: JsonFormat[sbt.internal.langserver.InitializeResult] = new JsonFormat[sbt.internal.langserver.InitializeResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.InitializeResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val capabilities = unbuilder.readField[sbt.internal.langserver.ServerCapabilities]("capabilities")
      unbuilder.endObject()
      sbt.internal.langserver.InitializeResult(capabilities)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.InitializeResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("capabilities", obj.capabilities)
    builder.endObject()
  }
}
}
