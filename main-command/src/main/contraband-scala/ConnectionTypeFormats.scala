/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ConnectionTypeFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ConnectionTypeFormat: JsonFormat[sbt.ConnectionType] = new JsonFormat[sbt.ConnectionType] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.ConnectionType = {
    jsOpt match {
      case Some(js) =>
      unbuilder.readString(js) match {
        case "Local" => sbt.ConnectionType.Local
        case "Tcp" => sbt.ConnectionType.Tcp
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.ConnectionType, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.ConnectionType.Local => "Local"
      case sbt.ConnectionType.Tcp => "Tcp"
    }
    builder.writeString(str)
  }
}
}
