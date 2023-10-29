/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement.ivy
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait IvyPathsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val IvyPathsFormat: JsonFormat[sbt.librarymanagement.ivy.IvyPaths] = new JsonFormat[sbt.librarymanagement.ivy.IvyPaths] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ivy.IvyPaths = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.beginObject(__js)
      val baseDirectory = unbuilder.readField[String]("baseDirectory")
      val ivyHome = unbuilder.readField[Option[String]]("ivyHome")
      unbuilder.endObject()
      sbt.librarymanagement.ivy.IvyPaths(baseDirectory, ivyHome)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ivy.IvyPaths, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("baseDirectory", obj.baseDirectory)
    builder.addField("ivyHome", obj.ivyHome)
    builder.endObject()
  }
}
}
