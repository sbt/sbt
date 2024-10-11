/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */
package sbt.librarymanagement

import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }

trait ConfigRefFormats { self: sjsonnew.BasicJsonProtocol =>
  implicit lazy val ConfigRefFormat: JsonFormat[sbt.librarymanagement.ConfigRef] =
    new JsonFormat[sbt.librarymanagement.ConfigRef] {
      override def read[J](
          __jsOpt: Option[J],
          unbuilder: Unbuilder[J]
      ): sbt.librarymanagement.ConfigRef = {
        __jsOpt match {
          case Some(__js) =>
            unbuilder.beginObject(__js)
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
