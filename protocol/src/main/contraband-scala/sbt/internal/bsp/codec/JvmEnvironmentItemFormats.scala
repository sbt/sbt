/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait JvmEnvironmentItemFormats { self: sbt.internal.bsp.codec.BuildTargetIdentifierFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val JvmEnvironmentItemFormat: JsonFormat[sbt.internal.bsp.JvmEnvironmentItem] = new JsonFormat[sbt.internal.bsp.JvmEnvironmentItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.JvmEnvironmentItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val target = unbuilder.readField[sbt.internal.bsp.BuildTargetIdentifier]("target")
      val classpath = unbuilder.readField[Vector[java.net.URI]]("classpath")
      val jvmOptions = unbuilder.readField[Vector[String]]("jvmOptions")
      val workingDirectory = unbuilder.readField[String]("workingDirectory")
      val environmentVariables = unbuilder.readField[scala.collection.immutable.Map[String, String]]("environmentVariables")
      unbuilder.endObject()
      sbt.internal.bsp.JvmEnvironmentItem(target, classpath, jvmOptions, workingDirectory, environmentVariables)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.JvmEnvironmentItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("target", obj.target)
    builder.addField("classpath", obj.classpath)
    builder.addField("jvmOptions", obj.jvmOptions)
    builder.addField("workingDirectory", obj.workingDirectory)
    builder.addField("environmentVariables", obj.environmentVariables)
    builder.endObject()
  }
}
}
