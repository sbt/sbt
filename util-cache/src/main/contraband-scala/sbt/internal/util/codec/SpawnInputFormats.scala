/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SpawnInputFormats { self: sjsonnew.BasicJsonProtocol =>
given SpawnInputFormat: JsonFormat[sbt.internal.util.SpawnInput] = new JsonFormat[sbt.internal.util.SpawnInput] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.util.SpawnInput = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val digest = unbuilder.readField[sbt.util.Digest]("digest")
      val codeContentHash = unbuilder.readField[sbt.util.Digest]("codeContentHash")
      val extraHash = unbuilder.readField[sbt.util.Digest]("extraHash")
      val cacheVersion = unbuilder.readField[Option[Long]]("cacheVersion")
      val str = unbuilder.readField[Option[String]]("str")
      unbuilder.endObject()
      sbt.internal.util.SpawnInput(digest, codeContentHash, extraHash, cacheVersion, str)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.util.SpawnInput, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("digest", obj.digest)
    builder.addField("codeContentHash", obj.codeContentHash)
    builder.addField("extraHash", obj.extraHash)
    builder.addField("cacheVersion", obj.cacheVersion)
    builder.addField("str", obj.str)
    builder.endObject()
  }
}
}
