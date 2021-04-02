/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaMainClassFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaMainClassFormat: JsonFormat[sbt.internal.bsp.ScalaMainClass] = new JsonFormat[sbt.internal.bsp.ScalaMainClass] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaMainClass = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val `class` = unbuilder.readField[String]("class")
      val arguments = unbuilder.readField[Vector[String]]("arguments")
      val jvmOptions = unbuilder.readField[Vector[String]]("jvmOptions")
      val environmentVariables = unbuilder.readField[Vector[String]]("environmentVariables")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaMainClass(`class`, arguments, jvmOptions, environmentVariables)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaMainClass, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("class", obj.`class`)
    builder.addField("arguments", obj.arguments)
    builder.addField("jvmOptions", obj.jvmOptions)
    builder.addField("environmentVariables", obj.environmentVariables)
    builder.endObject()
  }
}
}
