/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaWorkspaceEditFormats { self: sbt.internal.bsp.codec.ScalaTextEditFormats & sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaWorkspaceEditFormat: JsonFormat[sbt.internal.bsp.ScalaWorkspaceEdit] = new JsonFormat[sbt.internal.bsp.ScalaWorkspaceEdit] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaWorkspaceEdit = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val changes = unbuilder.readField[Vector[sbt.internal.bsp.ScalaTextEdit]]("changes")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaWorkspaceEdit(changes)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaWorkspaceEdit, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("changes", obj.changes)
    builder.endObject()
  }
}
}
