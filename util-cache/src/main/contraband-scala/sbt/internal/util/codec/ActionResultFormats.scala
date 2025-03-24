/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ActionResultFormats { self: sbt.internal.util.codec.HashedVirtualFileRefFormats & sbt.internal.util.codec.ByteBufferFormats & sjsonnew.BasicJsonProtocol =>
implicit lazy val ActionResultFormat: JsonFormat[sbt.util.ActionResult] = new JsonFormat[sbt.util.ActionResult] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.util.ActionResult = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val outputFiles = unbuilder.readField[Vector[xsbti.HashedVirtualFileRef]]("outputFiles")
      val origin = unbuilder.readField[Option[String]]("origin")
      val exitCode = unbuilder.readField[Option[Int]]("exitCode")
      val contents = unbuilder.readField[Vector[java.nio.ByteBuffer]]("contents")
      val isExecutable = unbuilder.readField[Vector[Boolean]]("isExecutable")
      unbuilder.endObject()
      sbt.util.ActionResult(outputFiles, origin, exitCode, contents, isExecutable)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.util.ActionResult, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("outputFiles", obj.outputFiles)
    builder.addField("origin", obj.origin)
    builder.addField("exitCode", obj.exitCode)
    builder.addField("contents", obj.contents)
    builder.addField("isExecutable", obj.isExecutable)
    builder.endObject()
  }
}
}
