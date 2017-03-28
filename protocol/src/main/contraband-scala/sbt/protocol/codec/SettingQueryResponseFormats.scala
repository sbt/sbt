/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait SettingQueryResponseFormats { self: sbt.internal.JValueFormat with sjsonnew.BasicJsonProtocol with sbt.protocol.codec.SettingQuerySuccessFormats with sbt.protocol.codec.SettingQueryFailureFormats =>
implicit lazy val SettingQueryResponseFormat: JsonFormat[sbt.protocol.SettingQueryResponse] = flatUnionFormat2[sbt.protocol.SettingQueryResponse, sbt.protocol.SettingQuerySuccess, sbt.protocol.SettingQueryFailure]("type")
}
