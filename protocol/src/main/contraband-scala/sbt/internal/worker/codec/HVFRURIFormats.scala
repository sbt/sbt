/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait HVFRURIFormats { self: sbt.internal.util.codec.HashedVirtualFileRefFormats & sjsonnew.BasicJsonProtocol =>
given HVFRURIFormat: JsonFormat[sbt.internal.worker.HVFRURI] = new JsonFormat[sbt.internal.worker.HVFRURI] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.HVFRURI = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[xsbti.HashedVirtualFileRef]("name")
      val value = unbuilder.readField[java.net.URI]("value")
      unbuilder.endObject()
      sbt.internal.worker.HVFRURI(name, value)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.HVFRURI, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("value", obj.value)
    builder.endObject()
  }
}
}
