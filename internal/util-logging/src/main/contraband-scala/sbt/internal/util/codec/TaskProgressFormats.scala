/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait TaskProgressFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val TaskProgressFormat: JsonFormat[sbt.internal.util.TaskProgress] = new JsonFormat[sbt.internal.util.TaskProgress] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.TaskProgress = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val elapsedMicros = unbuilder.readField[Option[Long]]("elapsedMicros")
      unbuilder.endObject()
      sbt.internal.util.TaskProgress(name, elapsedMicros)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.TaskProgress, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("elapsedMicros", obj.elapsedMicros)
    builder.endObject()
  }
}
}
