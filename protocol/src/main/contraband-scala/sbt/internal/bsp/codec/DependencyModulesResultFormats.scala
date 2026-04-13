/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DependencyModulesResultFormats { self: sbt.internal.bsp.codec.DependencyModulesItemFormats & sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.DependencyModuleFormats & sbt.internal.util.codec.JValueFormats =>
given DependencyModulesResultFormat: JsonFormat[sbt.internal.bsp.DependencyModulesResult] = new JsonFormat[sbt.internal.bsp.DependencyModulesResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.DependencyModulesResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val items = unbuilder.readField[Vector[sbt.internal.bsp.DependencyModulesItem]]("items")
      unbuilder.endObject()
      sbt.internal.bsp.DependencyModulesResult(items)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.DependencyModulesResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("items", obj.items)
    builder.endObject()
  }
}
}
