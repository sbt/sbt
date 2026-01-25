/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompileResponseFormats { self: sjsonnew.BasicJsonProtocol =>
given CompileResponseFormat: JsonFormat[sbt.internal.worker.CompileResponse] = new JsonFormat[sbt.internal.worker.CompileResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.worker.CompileResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val hasModified = unbuilder.readField[Boolean]("hasModified")
      unbuilder.endObject()
      sbt.internal.worker.CompileResponse(hasModified)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.worker.CompileResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("hasModified", obj.hasModified)
    builder.endObject()
  }
}
}
