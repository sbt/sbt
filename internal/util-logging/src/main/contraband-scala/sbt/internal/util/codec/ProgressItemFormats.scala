/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ProgressItemFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ProgressItemFormat: JsonFormat[sbt.internal.util.ProgressItem] = new JsonFormat[sbt.internal.util.ProgressItem] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.ProgressItem = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val elapsedMicros = unbuilder.readField[Long]("elapsedMicros")
      unbuilder.endObject()
      sbt.internal.util.ProgressItem(name, elapsedMicros)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.ProgressItem, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("elapsedMicros", obj.elapsedMicros)
    builder.endObject()
  }
}
}
