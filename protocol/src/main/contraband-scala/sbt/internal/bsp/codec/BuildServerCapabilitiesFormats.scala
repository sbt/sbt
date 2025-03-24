/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BuildServerCapabilitiesFormats { self: sbt.internal.bsp.codec.CompileProviderFormats & sjsonnew.BasicJsonProtocol & sbt.internal.bsp.codec.TestProviderFormats & sbt.internal.bsp.codec.RunProviderFormats & sbt.internal.bsp.codec.DebugProviderFormats =>
implicit lazy val BuildServerCapabilitiesFormat: JsonFormat[sbt.internal.bsp.BuildServerCapabilities] = new JsonFormat[sbt.internal.bsp.BuildServerCapabilities] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BuildServerCapabilities = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val compileProvider = unbuilder.readField[Option[sbt.internal.bsp.CompileProvider]]("compileProvider")
      val testProvider = unbuilder.readField[Option[sbt.internal.bsp.TestProvider]]("testProvider")
      val runProvider = unbuilder.readField[Option[sbt.internal.bsp.RunProvider]]("runProvider")
      val debugProvider = unbuilder.readField[Option[sbt.internal.bsp.DebugProvider]]("debugProvider")
      val dependencySourcesProvider = unbuilder.readField[Option[Boolean]]("dependencySourcesProvider")
      val resourcesProvider = unbuilder.readField[Option[Boolean]]("resourcesProvider")
      val outputPathsProvider = unbuilder.readField[Option[Boolean]]("outputPathsProvider")
      val canReload = unbuilder.readField[Option[Boolean]]("canReload")
      val jvmRunEnvironmentProvider = unbuilder.readField[Option[Boolean]]("jvmRunEnvironmentProvider")
      val jvmTestEnvironmentProvider = unbuilder.readField[Option[Boolean]]("jvmTestEnvironmentProvider")
      unbuilder.endObject()
      sbt.internal.bsp.BuildServerCapabilities(compileProvider, testProvider, runProvider, debugProvider, dependencySourcesProvider, resourcesProvider, outputPathsProvider, canReload, jvmRunEnvironmentProvider, jvmTestEnvironmentProvider)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BuildServerCapabilities, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("compileProvider", obj.compileProvider)
    builder.addField("testProvider", obj.testProvider)
    builder.addField("runProvider", obj.runProvider)
    builder.addField("debugProvider", obj.debugProvider)
    builder.addField("dependencySourcesProvider", obj.dependencySourcesProvider)
    builder.addField("resourcesProvider", obj.resourcesProvider)
    builder.addField("outputPathsProvider", obj.outputPathsProvider)
    builder.addField("canReload", obj.canReload)
    builder.addField("jvmRunEnvironmentProvider", obj.jvmRunEnvironmentProvider)
    builder.addField("jvmTestEnvironmentProvider", obj.jvmTestEnvironmentProvider)
    builder.endObject()
  }
}
}
