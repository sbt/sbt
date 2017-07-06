/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SettingQueryFailureFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SettingQueryFailureFormat: JsonFormat[sbt.protocol.SettingQueryFailure] = new JsonFormat[sbt.protocol.SettingQueryFailure] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.SettingQueryFailure = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.protocol.SettingQueryFailure(message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.SettingQueryFailure, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
