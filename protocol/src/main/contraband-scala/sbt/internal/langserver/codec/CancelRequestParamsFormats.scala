/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CancelRequestParamsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CancelRequestParamsFormat: JsonFormat[sbt.internal.langserver.CancelRequestParams] = new JsonFormat[sbt.internal.langserver.CancelRequestParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CancelRequestParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val id = unbuilder.readField[String]("id")
      unbuilder.endObject()
      sbt.internal.langserver.CancelRequestParams(id)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CancelRequestParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.endObject()
  }
}
}
