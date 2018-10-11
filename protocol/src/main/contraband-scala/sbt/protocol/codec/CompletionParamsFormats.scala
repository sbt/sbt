/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionParamsFormat: JsonFormat[sbt.protocol.CompletionParams] = new JsonFormat[sbt.protocol.CompletionParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.CompletionParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val query = unbuilder.readField[String]("query")
      unbuilder.endObject()
      sbt.protocol.CompletionParams(query)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.CompletionParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("query", obj.query)
    builder.endObject()
  }
}
}
