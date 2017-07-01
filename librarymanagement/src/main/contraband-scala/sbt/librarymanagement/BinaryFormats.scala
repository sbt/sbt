/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait BinaryFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val BinaryFormat: JsonFormat[sbt.librarymanagement.Binary] = new JsonFormat[sbt.librarymanagement.Binary] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Binary = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val prefix = unbuilder.readField[String]("prefix")
      val suffix = unbuilder.readField[String]("suffix")
      unbuilder.endObject()
      sbt.librarymanagement.Binary(prefix, suffix)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Binary, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("prefix", obj.prefix)
    builder.addField("suffix", obj.suffix)
    builder.endObject()
  }
}
}
