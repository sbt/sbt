/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SettingQueryFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SettingQueryFormat: JsonFormat[sbt.protocol.SettingQuery] = new JsonFormat[sbt.protocol.SettingQuery] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.SettingQuery = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val setting = unbuilder.readField[String]("setting")
      unbuilder.endObject()
      sbt.protocol.SettingQuery(setting)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.SettingQuery, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("setting", obj.setting)
    builder.endObject()
  }
}
}
