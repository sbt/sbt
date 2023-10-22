/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SbtBuildTargetFormats { self: sbt.internal.bsp.codec.ScalaBuildTargetFormats with sjsonnew.BasicJsonProtocol with sbt.internal.bsp.codec.BuildTargetIdentifierFormats =>
implicit lazy val SbtBuildTargetFormat: JsonFormat[sbt.internal.bsp.SbtBuildTarget] = new JsonFormat[sbt.internal.bsp.SbtBuildTarget] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.SbtBuildTarget = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val sbtVersion = unbuilder.readField[String]("sbtVersion")
      val autoImports = unbuilder.readField[Vector[String]]("autoImports")
      val scalaBuildTarget = unbuilder.readField[sbt.internal.bsp.ScalaBuildTarget]("scalaBuildTarget")
      val parent = unbuilder.readField[Option[sbt.internal.bsp.BuildTargetIdentifier]]("parent")
      val children = unbuilder.readField[Vector[sbt.internal.bsp.BuildTargetIdentifier]]("children")
      unbuilder.endObject()
      sbt.internal.bsp.SbtBuildTarget(sbtVersion, autoImports, scalaBuildTarget, parent, children)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.SbtBuildTarget, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("sbtVersion", obj.sbtVersion)
    builder.addField("autoImports", obj.autoImports)
    builder.addField("scalaBuildTarget", obj.scalaBuildTarget)
    builder.addField("parent", obj.parent)
    builder.addField("children", obj.children)
    builder.endObject()
  }
}
}
