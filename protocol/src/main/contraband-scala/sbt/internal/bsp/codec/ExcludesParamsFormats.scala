/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExcludesParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val ExcludesParamsFormat: JsonFormat[sbt.internal.bsp.ExcludesParams] = new JsonFormat[sbt.internal.bsp.ExcludesParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ExcludesParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("targets")
      unbuilder.endObject()
      sbt.internal.bsp.ExcludesParams(targets)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ExcludesParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.endObject()
  }
}
}
