/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.librarymanagement
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ScmInfoFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ScmInfoFormat: JsonFormat[sbt.librarymanagement.ScmInfo] = new JsonFormat[sbt.librarymanagement.ScmInfo] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.librarymanagement.ScmInfo = {
    jsOpt match {
      case Some(js) =>
      unbuilder.beginObject(js)
      val browseUrl = unbuilder.readField[java.net.URL]("browseUrl")
      val connection = unbuilder.readField[String]("connection")
      val devConnection = unbuilder.readField[Option[String]]("devConnection")
      unbuilder.endObject()
      sbt.librarymanagement.ScmInfo(browseUrl, connection, devConnection)
      case None =>
      deserializationError("Expected JsObject but found None")
    }
  }
  override def write[J](obj: sbt.librarymanagement.ScmInfo, builder: Builder[J]): Unit = {
    builder.beginObject()
    builder.addField("browseUrl", obj.browseUrl)
    builder.addField("connection", obj.connection)
    builder.addField("devConnection", obj.devConnection)
    builder.endObject()
  }
}
}
