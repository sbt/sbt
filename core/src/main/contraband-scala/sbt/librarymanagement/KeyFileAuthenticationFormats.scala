/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait KeyFileAuthenticationFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val KeyFileAuthenticationFormat: JsonFormat[sbt.librarymanagement.KeyFileAuthentication] = new JsonFormat[sbt.librarymanagement.KeyFileAuthentication] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.KeyFileAuthentication = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val user = unbuilder.readField[String]("user")
      val keyfile = unbuilder.readField[java.io.File]("keyfile")
      val password = unbuilder.readField[Option[String]]("password")
      unbuilder.endObject()
      sbt.librarymanagement.KeyFileAuthentication(user, keyfile, password)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.KeyFileAuthentication, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("user", obj.user)
    builder.addField("keyfile", obj.keyfile)
    builder.addField("password", obj.password)
    builder.endObject()
  }
}
}
