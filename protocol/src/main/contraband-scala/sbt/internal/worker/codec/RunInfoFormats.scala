/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RunInfoFormats { self: sbt.internal.worker.codec.FilePathFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val RunInfoFormat: JsonFormat[sbt.internal.worker.RunInfo] = new JsonFormat[sbt.internal.worker.RunInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.RunInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val jvm = unbuilder.readField[Boolean]("jvm")
      val args = unbuilder.readField[Vector[String]]("args")
      val classpath = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("classpath")
      val mainClass = unbuilder.readField[Option[String]]("mainClass")
      val connectInput = unbuilder.readField[Boolean]("connectInput")
      val javaHome = unbuilder.readField[Option[java.net.URI]]("javaHome")
      val outputStrategy = unbuilder.readField[Option[String]]("outputStrategy")
      val workingDirectory = unbuilder.readField[Option[java.net.URI]]("workingDirectory")
      val jvmOptions = unbuilder.readField[Vector[String]]("jvmOptions")
      val environmentVariables = unbuilder.readField[scala.collection.immutable.Map[String, String]]("environmentVariables")
      val inputs = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("inputs")
      val outputs = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("outputs")
      val cmd = unbuilder.readField[Option[String]]("cmd")
      unbuilder.endObject()
      sbt.internal.worker.RunInfo(jvm, args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs, cmd)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.RunInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("jvm", obj.jvm)
    builder.addField("args", obj.args)
    builder.addField("classpath", obj.classpath)
    builder.addField("mainClass", obj.mainClass)
    builder.addField("connectInput", obj.connectInput)
    builder.addField("javaHome", obj.javaHome)
    builder.addField("outputStrategy", obj.outputStrategy)
    builder.addField("workingDirectory", obj.workingDirectory)
    builder.addField("jvmOptions", obj.jvmOptions)
    builder.addField("environmentVariables", obj.environmentVariables)
    builder.addField("inputs", obj.inputs)
    builder.addField("outputs", obj.outputs)
    builder.addField("cmd", obj.cmd)
    builder.endObject()
  }
}
}
