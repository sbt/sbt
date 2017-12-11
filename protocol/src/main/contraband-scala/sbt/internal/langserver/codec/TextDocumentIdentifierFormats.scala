/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TextDocumentIdentifierFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TextDocumentIdentifierFormat: JsonFormat[sbt.internal.langserver.TextDocumentIdentifier] = new JsonFormat[sbt.internal.langserver.TextDocumentIdentifier] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.TextDocumentIdentifier = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val uri = unbuilder.readField[String]("uri")
      unbuilder.endObject()
      sbt.internal.langserver.TextDocumentIdentifier(uri)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.TextDocumentIdentifier, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.endObject()
  }
}
}
