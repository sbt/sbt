/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DependencyModulesItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.DependencyModuleFormats & sbt.internal.util.codec.JValueFormats =>
given DependencyModulesItemFormat: JsonFormat[sbt.internal.bsp.DependencyModulesItem] = new JsonFormat[sbt.internal.bsp.DependencyModulesItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.DependencyModulesItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[Option[sbt.internal.bsp.BuildTargetIdentifier]]("target")
      val modules = unbuilder.readField[Vector[sbt.internal.bsp.DependencyModule]]("modules")
      unbuilder.endObject()
      sbt.internal.bsp.DependencyModulesItem(target, modules)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.DependencyModulesItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("modules", obj.modules)
    builder.endObject()
  }
}
}
