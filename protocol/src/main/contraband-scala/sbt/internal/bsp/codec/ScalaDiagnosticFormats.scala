/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScalaDiagnosticFormats { self: sbt.internal.bsp.codec.ScalaActionFormats & sbt.internal.bsp.codec.ScalaWorkspaceEditFormats & sbt.internal.bsp.codec.ScalaTextEditFormats & sbt.internal.bsp.codec.RangeFormats & sbt.internal.bsp.codec.PositionFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ScalaDiagnosticFormat: JsonFormat[sbt.internal.bsp.ScalaDiagnostic] = new JsonFormat[sbt.internal.bsp.ScalaDiagnostic] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.bsp.ScalaDiagnostic = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val actions = unbuilder.readField[Vector[sbt.internal.bsp.ScalaAction]]("actions")
      unbuilder.endObject()
      sbt.internal.bsp.ScalaDiagnostic(actions)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.bsp.ScalaDiagnostic, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("actions", obj.actions)
    builder.endObject()
  }
}
}
