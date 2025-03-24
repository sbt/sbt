/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TextDocumentSyncOptionsFormats { self: sbt.internal.langserver.codec.SaveOptionsFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val TextDocumentSyncOptionsFormat: JsonFormat[sbt.internal.langserver.TextDocumentSyncOptions] = new JsonFormat[sbt.internal.langserver.TextDocumentSyncOptions] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.TextDocumentSyncOptions = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val openClose = unbuilder.readField[Option[Boolean]]("openClose")
      val change = unbuilder.readField[Option[Long]]("change")
      val willSave = unbuilder.readField[Option[Boolean]]("willSave")
      val willSaveWaitUntil = unbuilder.readField[Option[Boolean]]("willSaveWaitUntil")
      val save = unbuilder.readField[Option[sbt.internal.langserver.SaveOptions]]("save")
      unbuilder.endObject()
      sbt.internal.langserver.TextDocumentSyncOptions(openClose, change, willSave, willSaveWaitUntil, save)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.TextDocumentSyncOptions, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("openClose", obj.openClose)
    builder.addField("change", obj.change)
    builder.addField("willSave", obj.willSave)
    builder.addField("willSaveWaitUntil", obj.willSaveWaitUntil)
    builder.addField("save", obj.save)
    builder.endObject()
  }
}
}
