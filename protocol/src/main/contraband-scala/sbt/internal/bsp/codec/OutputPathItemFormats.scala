/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait OutputPathItemFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val OutputPathItemFormat: JsonFormat[sbt.internal.bsp.OutputPathItem] = new JsonFormat[sbt.internal.bsp.OutputPathItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.OutputPathItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[java.net.URI]("uri")
      val kind = unbuilder.readField[Int]("kind")
      unbuilder.endObject()
      sbt.internal.bsp.OutputPathItem(uri, kind)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.OutputPathItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("kind", obj.kind)
    builder.endObject()
  }
}
}
