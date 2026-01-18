/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait StringURIFormats { self: sjsonnew.BasicJsonProtocol =>
given StringURIFormat: JsonFormat[sbt.internal.worker.StringURI] = new JsonFormat[sbt.internal.worker.StringURI] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.StringURI = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val value = unbuilder.readField[java.net.URI]("value")
      unbuilder.endObject()
      sbt.internal.worker.StringURI(name, value)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.StringURI, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("value", obj.value)
    builder.endObject()
  }
}
}
