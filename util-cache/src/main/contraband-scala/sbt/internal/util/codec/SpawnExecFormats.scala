/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SpawnExecFormats { self: sbt.internal.util.codec.SpawnInputFormats & sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.HashedVirtualFileRefFormats =>
given SpawnExecFormat: JsonFormat[sbt.internal.util.SpawnExec] = new JsonFormat[sbt.internal.util.SpawnExec] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.SpawnExec = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val input = unbuilder.readField[sbt.internal.util.SpawnInput]("input")
      val cacheHit = unbuilder.readField[Boolean]("cacheHit")
      val exitCode = unbuilder.readField[Option[Int]]("exitCode")
      val outputs = unbuilder.readField[Vector[xsbti.HashedVirtualFileRef]]("outputs")
      unbuilder.endObject()
      sbt.internal.util.SpawnExec(input, cacheHit, exitCode, outputs)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.SpawnExec, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("input", obj.input)
    builder.addField("cacheHit", obj.cacheHit)
    builder.addField("exitCode", obj.exitCode)
    builder.addField("outputs", obj.outputs)
    builder.endObject()
  }
}
}
