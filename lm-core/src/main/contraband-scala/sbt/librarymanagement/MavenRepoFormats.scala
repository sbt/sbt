/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait MavenRepoFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val MavenRepoFormat: JsonFormat[sbt.librarymanagement.MavenRepo] = new JsonFormat[sbt.librarymanagement.MavenRepo] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.MavenRepo = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val name = unbuilder.readField[String]("name")
      val root = unbuilder.readField[String]("root")
      val localIfFile = unbuilder.readField[Boolean]("localIfFile")
      val _allowInsecureProtocol = unbuilder.readField[Boolean]("_allowInsecureProtocol")
      unbuilder.endObject()
      sbt.librarymanagement.MavenRepo(name, root, localIfFile, _allowInsecureProtocol)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.MavenRepo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("root", obj.root)
    builder.addField("localIfFile", obj.localIfFile)
    builder.addField("_allowInsecureProtocol", obj._allowInsecureProtocol)
    builder.endObject()
  }
}
}
