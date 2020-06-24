/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait AttachFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val AttachFormat: JsonFormat[sbt.protocol.Attach] = new JsonFormat[sbt.protocol.Attach] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.Attach = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val interactive = unbuilder.readField[Boolean]("interactive")
      unbuilder.endObject()
      sbt.protocol.Attach(interactive)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.Attach, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("interactive", obj.interactive)
    builder.endObject()
  }
}
}
