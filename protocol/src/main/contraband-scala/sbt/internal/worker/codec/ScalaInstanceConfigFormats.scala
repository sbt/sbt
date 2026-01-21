/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaInstanceConfigFormats { self: sjsonnew.BasicJsonProtocol =>
given ScalaInstanceConfigFormat: JsonFormat[sbt.internal.worker.ScalaInstanceConfig] = new JsonFormat[sbt.internal.worker.ScalaInstanceConfig] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.ScalaInstanceConfig = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val scalaVersion = unbuilder.readField[String]("scalaVersion")
      val libraryJars = unbuilder.readField[Vector[String]]("libraryJars")
      val allCompilerJars = unbuilder.readField[Vector[String]]("allCompilerJars")
      val allDocJars = unbuilder.readField[Vector[String]]("allDocJars")
      unbuilder.endObject()
      sbt.internal.worker.ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars, allDocJars)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.ScalaInstanceConfig, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("scalaVersion", obj.scalaVersion)
    builder.addField("libraryJars", obj.libraryJars)
    builder.addField("allCompilerJars", obj.allCompilerJars)
    builder.addField("allDocJars", obj.allDocJars)
    builder.endObject()
  }
}
}
