/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecCommandFormat: JsonFormat[sbt.protocol.ExecCommand] = new JsonFormat[sbt.protocol.ExecCommand] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecCommand = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val commandLine = unbuilder.readField[String]("commandLine")
      val execId = unbuilder.readField[Option[String]]("execId")
      unbuilder.endObject()
      sbt.protocol.ExecCommand(commandLine, execId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("commandLine", obj.commandLine)
    builder.addField("execId", obj.execId)
    builder.endObject()
  }
}
}
