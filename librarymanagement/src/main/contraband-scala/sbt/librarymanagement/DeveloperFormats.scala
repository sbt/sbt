/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait DeveloperFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val DeveloperFormat: JsonFormat[sbt.librarymanagement.Developer] = new JsonFormat[sbt.librarymanagement.Developer] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.Developer = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val id = unbuilder.readField[String]("id")
      val name = unbuilder.readField[String]("name")
      val email = unbuilder.readField[String]("email")
      val url = unbuilder.readField[java.net.URL]("url")
      unbuilder.endObject()
      sbt.librarymanagement.Developer(id, name, email, url)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.Developer, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("id", obj.id)
    builder.addField("name", obj.name)
    builder.addField("email", obj.email)
    builder.addField("url", obj.url)
    builder.endObject()
  }
}
}
