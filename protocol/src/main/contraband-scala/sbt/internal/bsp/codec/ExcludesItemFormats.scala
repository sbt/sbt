/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExcludesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats with sbt.internal.bsp.codec.ExcludeItemFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ExcludesItemFormat: JsonFormat[sbt.internal.bsp.ExcludesItem] = new JsonFormat[sbt.internal.bsp.ExcludesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ExcludesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val excludes = unbuilder.readField[Vector[sbt.internal.bsp.ExcludeItem]]("excludes")
      unbuilder.endObject()
      sbt.internal.bsp.ExcludesItem(target, excludes)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ExcludesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("excludes", obj.excludes)
    builder.endObject()
  }
}
}
