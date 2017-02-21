/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait CommandMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ExecCommandFormats =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.protocol.CommandMessage] = flatUnionFormat1[sbt.protocol.CommandMessage, sbt.protocol.ExecCommand]("type")
}
