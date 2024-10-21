/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JvmTestEnvironmentParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val JvmTestEnvironmentParamsFormat: JsonFormat[sbt.internal.bsp.JvmTestEnvironmentParams] = new JsonFormat[sbt.internal.bsp.JvmTestEnvironmentParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JvmTestEnvironmentParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("targets")
      val originId = unbuilder.readField[Option[String]]("originId")
      unbuilder.endObject()
      sbt.internal.bsp.JvmTestEnvironmentParams(targets, originId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JvmTestEnvironmentParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.addField("originId", obj.originId)
    builder.endObject()
  }
}
}
