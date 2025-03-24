/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DependencySourcesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val DependencySourcesItemFormat: JsonFormat[sbt.internal.bsp.DependencySourcesItem] = new JsonFormat[sbt.internal.bsp.DependencySourcesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.DependencySourcesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[Option[sbt.internal.bsp.BuildTargetIdentifier]]("target")
      val sources = unbuilder.readField[Vector[java.net.URI]]("sources")
      unbuilder.endObject()
      sbt.internal.bsp.DependencySourcesItem(target, sources)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.DependencySourcesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("sources", obj.sources)
    builder.endObject()
  }
}
}
