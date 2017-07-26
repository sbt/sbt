/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecCommandFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecCommandFormat: JsonFormat[sbt.internal.protocol.ExecCommand] = new JsonFormat[sbt.internal.protocol.ExecCommand] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.protocol.ExecCommand = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val commandLine = unbuilder.readField[String]("commandLine")
      val execId = unbuilder.readField[Option[String]]("execId")
      unbuilder.endObject()
      sbt.internal.protocol.ExecCommand(commandLine, execId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.protocol.ExecCommand, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("commandLine", obj.commandLine)
    builder.addField("execId", obj.execId)
    builder.endObject()
  }
}
}
