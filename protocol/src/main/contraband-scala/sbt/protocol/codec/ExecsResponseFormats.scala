/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ExecsResponseFormats { self: sbt.protocol.codec.ExecInfoFormats & sjsonnew.BasicJsonProtocol =>
given ExecsResponseFormat: JsonFormat[sbt.protocol.ExecsResponse] = new JsonFormat[sbt.protocol.ExecsResponse] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.protocol.ExecsResponse = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val execs = unbuilder.readField[Vector[sbt.protocol.ExecInfo]]("execs")
      unbuilder.endObject()
      sbt.protocol.ExecsResponse(execs)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.protocol.ExecsResponse, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("execs", obj.execs)
    builder.endObject()
  }
}
}
