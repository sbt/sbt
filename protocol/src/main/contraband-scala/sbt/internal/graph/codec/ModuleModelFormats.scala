/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.graph.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ModuleModelFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ModuleModelFormat: JsonFormat[sbt.internal.graph.ModuleModel] = new JsonFormat[sbt.internal.graph.ModuleModel] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.graph.ModuleModel = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val text = unbuilder.readField[String]("text")
      val children = unbuilder.readField[Vector[sbt.internal.graph.ModuleModel]]("children")
      unbuilder.endObject()
      sbt.internal.graph.ModuleModel(text, children)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.graph.ModuleModel, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("text", obj.text)
    builder.addField("children", obj.children)
    builder.endObject()
  }
}
}
