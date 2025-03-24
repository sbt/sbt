/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JvmRunInfoFormats { self: sbt.internal.worker.codec.FilePathFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val JvmRunInfoFormat: JsonFormat[sbt.internal.worker.JvmRunInfo] = new JsonFormat[sbt.internal.worker.JvmRunInfo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.JvmRunInfo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val args = unbuilder.readField[Vector[String]]("args")
      val classpath = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("classpath")
      val mainClass = unbuilder.readField[String]("mainClass")
      val connectInput = unbuilder.readField[Boolean]("connectInput")
      val javaHome = unbuilder.readField[Option[java.net.URI]]("javaHome")
      val outputStrategy = unbuilder.readField[Option[String]]("outputStrategy")
      val workingDirectory = unbuilder.readField[Option[java.net.URI]]("workingDirectory")
      val jvmOptions = unbuilder.readField[Vector[String]]("jvmOptions")
      val environmentVariables = unbuilder.readField[scala.collection.immutable.Map[String, String]]("environmentVariables")
      val inputs = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("inputs")
      val outputs = unbuilder.readField[Vector[sbt.internal.worker.FilePath]]("outputs")
      unbuilder.endObject()
      sbt.internal.worker.JvmRunInfo(args, classpath, mainClass, connectInput, javaHome, outputStrategy, workingDirectory, jvmOptions, environmentVariables, inputs, outputs)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.JvmRunInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
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
    builder.endObject()
  }
}
}
