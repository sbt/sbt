/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait LogMessageParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val LogMessageParamsFormat: JsonFormat[sbt.internal.langserver.LogMessageParams] = new JsonFormat[sbt.internal.langserver.LogMessageParams] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.LogMessageParams = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val `type` = unbuilder.readField[Long]("type")
      val message = unbuilder.readField[String]("message")
      unbuilder.endObject()
      sbt.internal.langserver.LogMessageParams(`type`, message)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.LogMessageParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("type", obj.`type`)
    builder.addField("message", obj.message)
    builder.endObject()
  }
}
}
