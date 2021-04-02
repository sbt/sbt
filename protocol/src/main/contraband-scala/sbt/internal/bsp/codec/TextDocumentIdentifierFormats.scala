/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TextDocumentIdentifierFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TextDocumentIdentifierFormat: JsonFormat[sbt.internal.bsp.TextDocumentIdentifier] = new JsonFormat[sbt.internal.bsp.TextDocumentIdentifier] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.TextDocumentIdentifier = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[java.net.URI]("uri")
      unbuilder.endObject()
      sbt.internal.bsp.TextDocumentIdentifier(uri)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.TextDocumentIdentifier, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.endObject()
  }
}
}
