/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait FullFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val FullFormat: JsonFormat[sbt.librarymanagement.Full] = new JsonFormat[sbt.librarymanagement.Full] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Full = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val prefix = unbuilder.readField[String]("prefix")
      val suffix = unbuilder.readField[String]("suffix")
      unbuilder.endObject()
      sbt.librarymanagement.Full(prefix, suffix)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Full, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("prefix", obj.prefix)
    builder.addField("suffix", obj.suffix)
    builder.endObject()
  }
}
}
