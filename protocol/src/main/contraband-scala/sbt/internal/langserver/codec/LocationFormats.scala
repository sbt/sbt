/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LocationFormats { self: sbt.internal.langserver.codec.RangeFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val LocationFormat: JsonFormat[sbt.internal.langserver.Location] = new JsonFormat[sbt.internal.langserver.Location] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Location = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val uri = unbuilder.readField[String]("uri")
      val range = unbuilder.readField[sbt.internal.langserver.Range]("range")
      unbuilder.endObject()
      sbt.internal.langserver.Location(uri, range)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Location, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("uri", obj.uri)
    builder.addField("range", obj.range)
    builder.endObject()
  }
}
}
