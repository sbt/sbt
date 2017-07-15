/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConfigRefFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ConfigRefFormat: JsonFormat[sbt.librarymanagement.ConfigRef] = new JsonFormat[sbt.librarymanagement.ConfigRef] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ConfigRef = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val name = unbuilder.readField[String]("name")
      unbuilder.endObject()
      sbt.librarymanagement.ConfigRef(name)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ConfigRef, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("name", obj.name)
    builder.endObject()
  }
}
}
