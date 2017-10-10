/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ServerAuthenticationFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ServerAuthenticationFormat: JsonFormat[sbt.ServerAuthentication] = new JsonFormat[sbt.ServerAuthentication] {
  override def read[J](jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.ServerAuthentication = {
    jsOpt match {
      case Some(js) =>
      unbuilder.readString(js) match {
        case "Token" => sbt.ServerAuthentication.Token
      }
      case None =>
      deserializationError("Expected JsString but found None")
    }
  }
  override def write[J](obj: sbt.ServerAuthentication, builder: Builder[J]): Unit = {
    val str = obj match {
      case sbt.ServerAuthentication.Token => "Token"
    }
    builder.writeString(str)
  }
}
}
