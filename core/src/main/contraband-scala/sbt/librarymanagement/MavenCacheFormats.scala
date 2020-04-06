/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait MavenCacheFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val MavenCacheFormat: JsonFormat[sbt.librarymanagement.MavenCache] = new JsonFormat[sbt.librarymanagement.MavenCache] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.MavenCache = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val root = unbuilder.readField[String]("root")
      val localIfFile = unbuilder.readField[Boolean]("localIfFile")
      val rootFile = unbuilder.readField[java.io.File]("rootFile")
      unbuilder.endObject()
      sbt.librarymanagement.MavenCache(name, root, localIfFile, rootFile)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.MavenCache, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("root", obj.root)
    builder.addField("localIfFile", obj.localIfFile)
    builder.addField("rootFile", obj.rootFile)
    builder.endObject()
  }
}
}
