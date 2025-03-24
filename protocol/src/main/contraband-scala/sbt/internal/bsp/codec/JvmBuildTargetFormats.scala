/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JvmBuildTargetFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val JvmBuildTargetFormat: JsonFormat[sbt.internal.bsp.JvmBuildTarget] = new JsonFormat[sbt.internal.bsp.JvmBuildTarget] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JvmBuildTarget = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val javaHome = unbuilder.readField[Option[java.net.URI]]("javaHome")
      val javaVersion = unbuilder.readField[Option[String]]("javaVersion")
      unbuilder.endObject()
      sbt.internal.bsp.JvmBuildTarget(javaHome, javaVersion)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JvmBuildTarget, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("javaHome", obj.javaHome)
    builder.addField("javaVersion", obj.javaVersion)
    builder.endObject()
  }
}
}
