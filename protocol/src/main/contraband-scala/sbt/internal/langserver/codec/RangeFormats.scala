/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait RangeFormats { self: sbt.internal.langserver.codec.PositionFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val RangeFormat: JsonFormat[sbt.internal.langserver.Range] = new JsonFormat[sbt.internal.langserver.Range] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.Range = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val start = unbuilder.readField[sbt.internal.langserver.Position]("start")
      val end = unbuilder.readField[sbt.internal.langserver.Position]("end")
      unbuilder.endObject()
      sbt.internal.langserver.Range(start, end)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.Range, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("start", obj.start)
    builder.addField("end", obj.end)
    builder.endObject()
  }
}
}
