/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait InitializeResultFormats { self: ServerCapabilitiesFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val InitializeResultFormat: JsonFormat[sbt.internal.langserver.InitializeResult] = new JsonFormat[sbt.internal.langserver.InitializeResult] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.InitializeResult = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val capabilities = unbuilder.readField[Option[sbt.internal.langserver.ServerCapabilities]]("capabilities")
      unbuilder.endObject()
      sbt.internal.langserver.InitializeResult(capabilities)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.InitializeResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("capabilities", obj.capabilities)
    builder.endObject()
  }
}
}
