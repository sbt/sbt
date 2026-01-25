/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompileConfigFormats { self: sbt.internal.worker.codec.FileConverterConfigFormats & sbt.internal.worker.codec.StringURIFormats & sjsonnew.BasicJsonProtocol & sbt.internal.worker.codec.ScalaInstanceConfigFormats & sbt.internal.worker.codec.HVFRURIFormats & sbt.internal.util.codec.HashedVirtualFileRefFormats =>
given CompileConfigFormat: JsonFormat[sbt.internal.worker.CompileConfig] = new JsonFormat[sbt.internal.worker.CompileConfig] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.CompileConfig = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val fileConverterConfig = unbuilder.readField[sbt.internal.worker.FileConverterConfig]("fileConverterConfig")
      val scalaInstanceConfig = unbuilder.readField[sbt.internal.worker.ScalaInstanceConfig]("scalaInstanceConfig")
      val bridgeJars = unbuilder.readField[Vector[java.net.URI]]("bridgeJars")
      val sources = unbuilder.readField[Vector[String]]("sources")
      val externalDependencyJars = unbuilder.readField[Vector[String]]("externalDependencyJars")
      val output = unbuilder.readField[java.net.URI]("output")
      val analysisFile = unbuilder.readField[java.net.URI]("analysisFile")
      val earlyJarPath = unbuilder.readField[Option[java.net.URI]]("earlyJarPath")
      val scalacOptions = unbuilder.readField[Vector[String]]("scalacOptions")
      val javacOptions = unbuilder.readField[Vector[String]]("javacOptions")
      val maxErrors = unbuilder.readField[Int]("maxErrors")
      val analysisMap = unbuilder.readField[Vector[sbt.internal.worker.HVFRURI]]("analysisMap")
      unbuilder.endObject()
      sbt.internal.worker.CompileConfig(fileConverterConfig, scalaInstanceConfig, bridgeJars, sources, externalDependencyJars, output, analysisFile, earlyJarPath, scalacOptions, javacOptions, maxErrors, analysisMap)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.CompileConfig, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("fileConverterConfig", obj.fileConverterConfig)
    builder.addField("scalaInstanceConfig", obj.scalaInstanceConfig)
    builder.addField("bridgeJars", obj.bridgeJars)
    builder.addField("sources", obj.sources)
    builder.addField("externalDependencyJars", obj.externalDependencyJars)
    builder.addField("output", obj.output)
    builder.addField("analysisFile", obj.analysisFile)
    builder.addField("earlyJarPath", obj.earlyJarPath)
    builder.addField("scalacOptions", obj.scalacOptions)
    builder.addField("javacOptions", obj.javacOptions)
    builder.addField("maxErrors", obj.maxErrors)
    builder.addField("analysisMap", obj.analysisMap)
    builder.endObject()
  }
}
}
