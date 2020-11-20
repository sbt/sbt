/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
import _root_.sjsonnew.{ Unbuilder, Builder, JsonFormat, deserializationError }
trait ServerAuthenticationFormats { self: sjsonnew.BasicJsonProtocol =>
implicit lazy val ServerAuthenticationFormat: JsonFormat[sbt.ServerAuthentication] = new JsonFormat[sbt.ServerAuthentication] {
  override def read[J](__jsOpt: Option[J], unbuilder: Unbuilder[J]): sbt.ServerAuthentication = {
    __jsOpt match {
      case Some(__js) =>
      unbuilder.readString(__js) match {
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
