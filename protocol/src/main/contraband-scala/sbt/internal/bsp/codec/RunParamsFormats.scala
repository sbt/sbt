/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RunParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.JValueFormats =>
implicit lazy val RunParamsFormat: JsonFormat[sbt.internal.bsp.RunParams] = new JsonFormat[sbt.internal.bsp.RunParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.RunParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val originId = unbuilder.readField[Option[String]]("originId")
      val arguments = unbuilder.readField[Vector[String]]("arguments")
      val dataKind = unbuilder.readField[Option[String]]("dataKind")
      val data = unbuilder.readField[Option[sjsonnew.shaded.scalajson.ast.unsafe.JValue]]("data")
      unbuilder.endObject()
      sbt.internal.bsp.RunParams(target, originId, arguments, dataKind, data)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.RunParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("originId", obj.originId)
    builder.addField("arguments", obj.arguments)
    builder.addField("dataKind", obj.dataKind)
    builder.addField("data", obj.data)
    builder.endObject()
  }
}
}
