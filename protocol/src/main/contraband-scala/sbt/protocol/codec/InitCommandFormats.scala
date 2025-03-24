/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitCommandFormats { self: sbt.internal.protocol.codec.InitializeOptionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val InitCommandFormat: JsonFormat[sbt.protocol.InitCommand] = new JsonFormat[sbt.protocol.InitCommand] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.InitCommand = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val token = unbuilder.readField[Option[String]]("token")
      val execId = unbuilder.readField[Option[String]]("execId")
      val skipAnalysis = unbuilder.readField[Option[Boolean]]("skipAnalysis")
      val initializationOptions = unbuilder.readField[Option[sbt.internal.protocol.InitializeOption]]("initializationOptions")
      unbuilder.endObject()
      sbt.protocol.InitCommand(token, execId, skipAnalysis, initializationOptions)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.InitCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("token", obj.token)
    builder.addField("execId", obj.execId)
    builder.addField("skipAnalysis", obj.skipAnalysis)
    builder.addField("initializationOptions", obj.initializationOptions)
    builder.endObject()
  }
}
}
