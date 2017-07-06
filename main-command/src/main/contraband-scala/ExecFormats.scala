/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecFormats { self: CommandSourceFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ExecFormat: JsonFormat[sbt.Exec] = new JsonFormat[sbt.Exec] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.Exec = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
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
