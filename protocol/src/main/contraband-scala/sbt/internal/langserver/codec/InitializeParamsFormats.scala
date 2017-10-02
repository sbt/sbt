/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeParamsFormats { self: sbt.internal.util.codec.JValueFormats with sbt.internal.langserver.codec.ClientCapabilitiesFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeParamsFormat: JsonFormat[sbt.internal.langserver.InitializeParams] = new JsonFormat[sbt.internal.langserver.InitializeParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.InitializeParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val processId = unbuilder.readField[Option[Long]]("processId")
      val rootPath = unbuilder.readField[Option[String]]("rootPath")
      val rootUri = unbuilder.readField[Option[String]]("rootUri")
      val initializationOptions = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("initializationOptions")
      val capabilities = unbuilder.readField[Option[sbt.internal.langserver.ClientCapabilities]]("capabilities")
      val trace = unbuilder.readField[Option[String]]("trace")
      unbuilder.endObject()
      sbt.internal.langserver.InitializeParams(processId, rootPath, rootUri, initializationOptions, capabilities, trace)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.InitializeParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("processId", obj.processId)
    builder.addField("rootPath", obj.rootPath)
    builder.addField("rootUri", obj.rootUri)
    builder.addField("initializationOptions", obj.initializationOptions)
    builder.addField("capabilities", obj.capabilities)
    builder.addField("trace", obj.trace)
    builder.endObject()
  }
}
}
