/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionParamsFormats { self: sbt.internal.langserver.codec.CompletionContextFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionParamsFormat: JsonFormat[sbt.internal.langserver.CompletionParams] = new JsonFormat[sbt.internal.langserver.CompletionParams] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CompletionParams = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val context = unbuilder.readField[Option[sbt.internal.langserver.CompletionContext]]("context")
      unbuilder.endObject()
      sbt.internal.langserver.CompletionParams(context)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CompletionParams, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("context", obj.context)
    builder.endObject()
  }
}
}
