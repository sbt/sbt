/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ClientCapabilitiesFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ClientCapabilitiesFormat: JsonFormat[sbt.internal.langserver.ClientCapabilities] = new JsonFormat[sbt.internal.langserver.ClientCapabilities] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.ClientCapabilities = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      
      unbuilder.endObject()
      sbt.internal.langserver.ClientCapabilities()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.ClientCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
