/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SbtExecParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SbtExecParamsFormat: JsonFormat[sbt.internal.langserver.SbtExecParams] = new JsonFormat[sbt.internal.langserver.SbtExecParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.SbtExecParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val commandLine = unbuilder.readField[String]("commandLine")
      unbuilder.endObject()
      sbt.internal.langserver.SbtExecParams(commandLine)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.SbtExecParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("commandLine", obj.commandLine)
    builder.endObject()
  }
}
}
