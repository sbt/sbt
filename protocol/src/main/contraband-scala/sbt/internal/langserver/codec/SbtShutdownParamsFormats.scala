/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SbtShutdownParamsFormats { self: sjsonnew.BasicJsonProtocol =>
given SbtShutdownParamsFormat: JsonFormat[sbt.internal.langserver.SbtShutdownParams] = new JsonFormat[sbt.internal.langserver.SbtShutdownParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.SbtShutdownParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val force = unbuilder.readField[Option[Boolean]]("force")
      unbuilder.endObject()
      sbt.internal.langserver.SbtShutdownParams(force)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.SbtShutdownParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("force", obj.force)
    builder.endObject()
  }
}
}
