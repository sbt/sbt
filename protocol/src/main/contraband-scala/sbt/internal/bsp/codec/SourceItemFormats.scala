/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SourceItemFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val SourceItemFormat: JsonFormat[sbt.internal.bsp.SourceItem] = new JsonFormat[sbt.internal.bsp.SourceItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.SourceItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val uri = unbuilder.readField[java.net.URI]("uri")
      val kind = unbuilder.readField[Int]("kind")
      val generated = unbuilder.readField[Boolean]("generated")
      unbuilder.endObject()
      sbt.internal.bsp.SourceItem(uri, kind, generated)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.SourceItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("kind", obj.kind)
    builder.addField("generated", obj.generated)
    builder.endObject()
  }
}
}
