/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CleanCacheParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val CleanCacheParamsFormat: JsonFormat[sbt.internal.bsp.CleanCacheParams] = new JsonFormat[sbt.internal.bsp.CleanCacheParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.CleanCacheParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("targets")
      unbuilder.endObject()
      sbt.internal.bsp.CleanCacheParams(targets)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.CleanCacheParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.endObject()
  }
}
}
