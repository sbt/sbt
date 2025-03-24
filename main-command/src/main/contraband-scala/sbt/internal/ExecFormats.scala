/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecFormats { self: sbt.internal.CommandSourceFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecFormat: JsonFormat[sbt.Exec] = new JsonFormat[sbt.Exec] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.Exec = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val commandLine = unbuilder.readField[String]("commandLine")
      val execId = unbuilder.readField[Option[String]]("execId")
      val source = unbuilder.readField[Option[sbt.CommandSource]]("source")
      unbuilder.endObject()
      sbt.Exec(commandLine, execId, source)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.Exec, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("commandLine", obj.commandLine)
    builder.addField("execId", obj.execId)
    builder.addField("source", obj.source)
    builder.endObject()
  }
}
}
