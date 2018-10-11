/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionResponseFormat: JsonFormat[sbt.protocol.CompletionResponse] = new JsonFormat[sbt.protocol.CompletionResponse] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.CompletionResponse = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val items = unbuilder.readField[Vector[String]]("items")
      unbuilder.endObject()
      sbt.protocol.CompletionResponse(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.CompletionResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
