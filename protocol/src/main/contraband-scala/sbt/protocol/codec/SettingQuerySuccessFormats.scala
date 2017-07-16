/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SettingQuerySuccessFormats { self: sbt.internal.util.codec.JValueFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val SettingQuerySuccessFormat: JsonFormat[sbt.protocol.SettingQuerySuccess] = new JsonFormat[sbt.protocol.SettingQuerySuccess] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.SettingQuerySuccess = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val value = unbuilder.readField[sjsonnew.shaded.scalajson.ast.unsafe.JValue]("value")
      val contentType = unbuilder.readField[String]("contentType")
      unbuilder.endObject()
      sbt.protocol.SettingQuerySuccess(value, contentType)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.SettingQuerySuccess, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("value", obj.value)
    builder.addField("contentType", obj.contentType)
    builder.endObject()
  }
}
}
