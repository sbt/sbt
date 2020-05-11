/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildTargetCapabilitiesFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildTargetCapabilitiesFormat: JsonFormat[sbt.internal.bsp.BuildTargetCapabilities] = new JsonFormat[sbt.internal.bsp.BuildTargetCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildTargetCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val canCompile = unbuilder.readField[Boolean]("canCompile")
      val canTest = unbuilder.readField[Boolean]("canTest")
      val canRun = unbuilder.readField[Boolean]("canRun")
      unbuilder.endObject()
      sbt.internal.bsp.BuildTargetCapabilities(canCompile, canTest, canRun)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildTargetCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("canCompile", obj.canCompile)
    builder.addField("canTest", obj.canTest)
    builder.addField("canRun", obj.canRun)
    builder.endObject()
  }
}
}
