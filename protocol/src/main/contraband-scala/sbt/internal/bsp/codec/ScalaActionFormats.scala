/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaActionFormats { self: sbt.internal.bsp.codec.ScalaWorkspaceEditFormats & sbt.internal.bsp.codec.ScalaTextEditFormats & sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaActionFormat: JsonFormat[sbt.internal.bsp.ScalaAction] = new JsonFormat[sbt.internal.bsp.ScalaAction] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaAction = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val title = unbuilder.readField[String]("title")
      val description = unbuilder.readField[Option[String]]("description")
      val edit = unbuilder.readField[Option[sbt.internal.bsp.ScalaWorkspaceEdit]]("edit")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaAction(title, description, edit)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaAction, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("title", obj.title)
    builder.addField("description", obj.description)
    builder.addField("edit", obj.edit)
    builder.endObject()
  }
}
}
