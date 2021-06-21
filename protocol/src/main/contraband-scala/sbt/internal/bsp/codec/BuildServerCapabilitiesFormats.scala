/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildServerCapabilitiesFormats { self: sbt.internal.bsp.codec.CompileProviderFormats with sbt.internal.bsp.codec.TestProviderFormats with sbt.internal.bsp.codec.RunProviderFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val BuildServerCapabilitiesFormat: JsonFormat[sbt.internal.bsp.BuildServerCapabilities] = new JsonFormat[sbt.internal.bsp.BuildServerCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildServerCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val compileProvider = unbuilder.readField[Option[sbt.internal.bsp.CompileProvider]]("compileProvider")
      val testProvider = unbuilder.readField[Option[sbt.internal.bsp.TestProvider]]("testProvider")
      val runProvider = unbuilder.readField[Option[sbt.internal.bsp.RunProvider]]("runProvider")
      val dependencySourcesProvider = unbuilder.readField[Option[Boolean]]("dependencySourcesProvider")
      val resourcesProvider = unbuilder.readField[Option[Boolean]]("resourcesProvider")
      val canReload = unbuilder.readField[Option[Boolean]]("canReload")
      unbuilder.endObject()
      sbt.internal.bsp.BuildServerCapabilities(compileProvider, testProvider, runProvider, dependencySourcesProvider, resourcesProvider, canReload)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildServerCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("compileProvider", obj.compileProvider)
    builder.addField("testProvider", obj.testProvider)
    builder.addField("runProvider", obj.runProvider)
    builder.addField("dependencySourcesProvider", obj.dependencySourcesProvider)
    builder.addField("resourcesProvider", obj.resourcesProvider)
    builder.addField("canReload", obj.canReload)
    builder.endObject()
  }
}
}
