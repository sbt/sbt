/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait PasswordAuthenticationFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val PasswordAuthenticationFormat: JsonFormat[sbt.librarymanagement.PasswordAuthentication] = new JsonFormat[sbt.librarymanagement.PasswordAuthentication] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.PasswordAuthentication = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val user = unbuilder.readField[String]("user")
      val password = unbuilder.readField[Option[String]]("password")
      unbuilder.endObject()
      sbt.librarymanagement.PasswordAuthentication(user, password)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.PasswordAuthentication, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("user", obj.user)
    builder.addField("password", obj.password)
    builder.endObject()
  }
}
}
