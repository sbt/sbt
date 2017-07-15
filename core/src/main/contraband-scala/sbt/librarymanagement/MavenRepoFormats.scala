/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait MavenRepoFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val MavenRepoFormat: JsonFormat[sbt.librarymanagement.MavenRepo] = new JsonFormat[sbt.librarymanagement.MavenRepo] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.MavenRepo = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val root = unbuilder.readField[String]("root")
      val localIfFile = unbuilder.readField[Boolean]("localIfFile")
      unbuilder.endObject()
      sbt.librarymanagement.MavenRepo(name, root, localIfFile)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.MavenRepo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("root", obj.root)
    builder.addField("localIfFile", obj.localIfFile)
    builder.endObject()
  }
}
}
