/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait WorkspaceBuildTargetsResultFormats { self: sbt.internal.bsp.codec.BuildTargetFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.BuildTargetCapabilitiesFormats & sbt.internal.util.codec.JValueFormats =>
implicit lazy val WorkspaceBuildTargetsResultFormat: JsonFormat[sbt.internal.bsp.WorkspaceBuildTargetsResult] = new JsonFormat[sbt.internal.bsp.WorkspaceBuildTargetsResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.WorkspaceBuildTargetsResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val targets = unbuilder.readField[Vector[sbt.internal.bsp.BuildTarget]]("targets")
      unbuilder.endObject()
      sbt.internal.bsp.WorkspaceBuildTargetsResult(targets)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.WorkspaceBuildTargetsResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("targets", obj.targets)
    builder.endObject()
  }
}
}
