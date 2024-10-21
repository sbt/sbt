/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JvmRunEnvironmentResultFormats { self: sbt.internal.bsp.codec.JvmEnvironmentItemFormats with sbt.internal.bsp.codec.BuildTargetIdentifierFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val JvmRunEnvironmentResultFormat: JsonFormat[sbt.internal.bsp.JvmRunEnvironmentResult] = new JsonFormat[sbt.internal.bsp.JvmRunEnvironmentResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JvmRunEnvironmentResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.JvmEnvironmentItem]]("items")
      val originId = unbuilder.readField[Option[String]]("originId")
      unbuilder.endObject()
      sbt.internal.bsp.JvmRunEnvironmentResult(items, originId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JvmRunEnvironmentResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.addField("originId", obj.originId)
    builder.endObject()
  }
}
}
