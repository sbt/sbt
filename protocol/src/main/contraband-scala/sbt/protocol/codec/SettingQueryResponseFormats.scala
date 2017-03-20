/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait SettingQueryResponseFormats { self: sbt.internal.JValueFormat with sjsonnew.BasicJsonProtocol =>
implicit lazy val SettingQueryResponseFormat: JsonFormat[sbt.protocol.SettingQueryResponse] = new JsonFormat[sbt.protocol.SettingQueryResponse] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.SettingQueryResponse = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val value = unbuilder.readField[scala.json.ast.unsafe.JValue]("value")
      unbuilder.endObject()
      sbt.protocol.SettingQueryResponse(value)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.SettingQueryResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("value", obj.value)
    builder.endObject()
  }
}
}
