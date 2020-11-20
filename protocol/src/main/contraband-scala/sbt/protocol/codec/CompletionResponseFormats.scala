/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionResponseFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionResponseFormat: JsonFormat[sbt.protocol.CompletionResponse] = new JsonFormat[sbt.protocol.CompletionResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.CompletionResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[String]]("items")
      val cachedMainClassNames = unbuilder.readField[Option[Boolean]]("cachedMainClassNames")
      val cachedTestNames = unbuilder.readField[Option[Boolean]]("cachedTestNames")
      unbuilder.endObject()
      sbt.protocol.CompletionResponse(items, cachedMainClassNames, cachedTestNames)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.CompletionResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.addField("cachedMainClassNames", obj.cachedMainClassNames)
    builder.addField("cachedTestNames", obj.cachedTestNames)
    builder.endObject()
  }
}
}
