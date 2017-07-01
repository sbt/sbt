/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait IvyPathsFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val IvyPathsFormat: JsonFormat[sbt.internal.librarymanagement.IvyPaths] = new JsonFormat[sbt.internal.librarymanagement.IvyPaths] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.internal.librarymanagement.IvyPaths = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val baseDirectory = unbuilder.readField[java.io.File]("baseDirectory")
      val ivyHome = unbuilder.readField[Option[java.io.File]]("ivyHome")
      unbuilder.endObject()
      sbt.internal.librarymanagement.IvyPaths(baseDirectory, ivyHome)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.internal.librarymanagement.IvyPaths, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("baseDirectory", obj.baseDirectory)
    builder.addField("ivyHome", obj.ivyHome)
    builder.endObject()
  }
}
}
