/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BspConnectionDetailsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BspConnectionDetailsFormat: JsonFormat[sbt.internal.bsp.BspConnectionDetails] = new JsonFormat[sbt.internal.bsp.BspConnectionDetails] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.BspConnectionDetails = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val version = unbuilder.readField[String]("version")
      val bspVersion = unbuilder.readField[String]("bspVersion")
      val languages = unbuilder.readField[Vector[String]]("languages")
      val argv = unbuilder.readField[Vector[String]]("argv")
      unbuilder.endObject()
      sbt.internal.bsp.BspConnectionDetails(name, version, bspVersion, languages, argv)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.BspConnectionDetails, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("version", obj.version)
    builder.addField("bspVersion", obj.bspVersion)
    builder.addField("languages", obj.languages)
    builder.addField("argv", obj.argv)
    builder.endObject()
  }
}
}
