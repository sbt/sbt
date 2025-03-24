/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaTestClassesParamsFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaTestClassesParamsFormat: JsonFormat[sbt.internal.bsp.ScalaTestClassesParams] = new JsonFormat[sbt.internal.bsp.ScalaTestClassesParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaTestClassesParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("targets")
      val originId = unbuilder.readField[Option[String]]("originId")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaTestClassesParams(targets, originId)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaTestClassesParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.addField("originId", obj.originId)
    builder.endObject()
  }
}
}
