/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait CompletionContextFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val CompletionContextFormat: JsonFormat[sbt.internal.langserver.CompletionContext] = new JsonFormat[sbt.internal.langserver.CompletionContext] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.langserver.CompletionContext = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val triggerKind = unbuilder.readField[Int]("triggerKind")
      val triggerCharacter = unbuilder.readField[Option[String]]("triggerCharacter")
      unbuilder.endObject()
      sbt.internal.langserver.CompletionContext(triggerKind, triggerCharacter)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.langserver.CompletionContext, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("triggerKind", obj.triggerKind)
    builder.addField("triggerCharacter", obj.triggerCharacter)
    builder.endObject()
  }
}
}
