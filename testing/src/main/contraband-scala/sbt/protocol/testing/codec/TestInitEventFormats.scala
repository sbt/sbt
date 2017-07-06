/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TestInitEventFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TestInitEventFormat: JsonFormat[sbt.protocol.testing.TestInitEvent] = new JsonFormat[sbt.protocol.testing.TestInitEvent] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.testing.TestInitEvent = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      
      unbuilder.endObject()
      sbt.protocol.testing.TestInitEvent()
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.testing.TestInitEvent, builder: Builder[J]): Unit = {
    builder.beginObject()
    
    builder.endObject()
  }
}
}
