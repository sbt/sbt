/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SettingQuerySuccessFormats { self: sbt.internal.util.codec.JValueFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val SettingQuerySuccessFormat: JsonFormat[sbt.protocol.SettingQuerySuccess] = new JsonFormat[sbt.protocol.SettingQuerySuccess] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.SettingQuerySuccess = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
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
