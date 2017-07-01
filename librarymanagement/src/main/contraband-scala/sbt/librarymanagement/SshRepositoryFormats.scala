/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait SshRepositoryFormats { self: sbt.librarymanagement.PatternsFormats with sbt.librarymanagement.SshConnectionFormats with sjsonnew.BasicJsonProtocol =>
implicit lazy val SshRepositoryFormat: JsonFormat[sbt.librarymanagement.SshRepository] = new JsonFormat[sbt.librarymanagement.SshRepository] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.SshRepository = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      val patterns = unbuilder.readField[sbt.librarymanagement.Patterns]("patterns")
      val connection = unbuilder.readField[sbt.librarymanagement.SshConnection]("connection")
      val publishPermissions = unbuilder.readField[Option[String]]("publishPermissions")
      unbuilder.endObject()
      sbt.librarymanagement.SshRepository(name, patterns, connection, publishPermissions)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.SshRepository, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.addField("patterns", obj.patterns)
    builder.addField("connection", obj.connection)
    builder.addField("publishPermissions", obj.publishPermissions)
    builder.endObject()
  }
}
}
