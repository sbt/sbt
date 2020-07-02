/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionParamsFormat: JsonFormat[sbt.protocol.CompletionParams] = new JsonFormat[sbt.protocol.CompletionParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.CompletionParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val query = unbuilder.readField[String]("query")
      val level = unbuilder.readField[Option[Int]]("level")
      unbuilder.endObject()
      sbt.protocol.CompletionParams(query, level)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.CompletionParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("query", obj.query)
    builder.addField("level", obj.level)
    builder.endObject()
  }
}
}
