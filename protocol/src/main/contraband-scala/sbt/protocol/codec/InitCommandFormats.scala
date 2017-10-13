/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val InitCommandFormat: JsonFormat[sbt.protocol.InitCommand] = new JsonFormat[sbt.protocol.InitCommand] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.InitCommand = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val token = unbuilder.readField[Option[String]]("token")
      val execId = unbuilder.readField[Option[String]]("execId")
      unbuilder.endObject()
      sbt.protocol.InitCommand(token, execId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.InitCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("token", obj.token)
    builder.addField("execId", obj.execId)
    builder.endObject()
  }
}
}
