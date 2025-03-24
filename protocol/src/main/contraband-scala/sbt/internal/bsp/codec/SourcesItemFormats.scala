/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SourcesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.SourceItemFormats =>
implicit lazy val SourcesItemFormat: JsonFormat[sbt.internal.bsp.SourcesItem] = new JsonFormat[sbt.internal.bsp.SourcesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.SourcesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val sources = unbuilder.readField[Vector[sbt.internal.bsp.SourceItem]]("sources")
      unbuilder.endObject()
      sbt.internal.bsp.SourcesItem(target, sources)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.SourcesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("sources", obj.sources)
    builder.endObject()
  }
}
}
