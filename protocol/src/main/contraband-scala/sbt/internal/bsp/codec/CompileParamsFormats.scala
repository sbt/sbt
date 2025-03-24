/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompileParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val CompileParamsFormat: JsonFormat[sbt.internal.bsp.CompileParams] = new JsonFormat[sbt.internal.bsp.CompileParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.CompileParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("targets")
      val originId = unbuilder.readField[Option[String]]("originId")
      val arguments = unbuilder.readField[Vector[String]]("arguments")
      unbuilder.endObject()
      sbt.internal.bsp.CompileParams(targets, originId, arguments)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.CompileParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.addField("originId", obj.originId)
    builder.addField("arguments", obj.arguments)
    builder.endObject()
  }
}
}
