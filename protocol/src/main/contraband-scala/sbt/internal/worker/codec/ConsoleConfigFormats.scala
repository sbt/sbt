/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConsoleConfigFormats { self: sbt.internal.worker.codec.ScalaInstanceConfigFormats & sjsonnew.BasicJsonProtocol =>
given ConsoleConfigFormat: JsonFormat[sbt.internal.worker.ConsoleConfig] = new JsonFormat[sbt.internal.worker.ConsoleConfig] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.ConsoleConfig = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val scalaInstanceConfig = unbuilder.readField[sbt.internal.worker.ScalaInstanceConfig]("scalaInstanceConfig")
      val bridgeJars = unbuilder.readField[Vector[java.net.URI]]("bridgeJars")
      val externalDependencyJars = unbuilder.readField[Vector[String]]("externalDependencyJars")
      val scalacOptions = unbuilder.readField[Vector[String]]("scalacOptions")
      val initialCommands = unbuilder.readField[String]("initialCommands")
      val cleanupCommands = unbuilder.readField[String]("cleanupCommands")
      unbuilder.endObject()
      sbt.internal.worker.ConsoleConfig(scalaInstanceConfig, bridgeJars, externalDependencyJars, scalacOptions, initialCommands, cleanupCommands)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.ConsoleConfig, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("scalaInstanceConfig", obj.scalaInstanceConfig)
    builder.addField("bridgeJars", obj.bridgeJars)
    builder.addField("externalDependencyJars", obj.externalDependencyJars)
    builder.addField("scalacOptions", obj.scalacOptions)
    builder.addField("initialCommands", obj.initialCommands)
    builder.addField("cleanupCommands", obj.cleanupCommands)
    builder.endObject()
  }
}
}
