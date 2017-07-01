/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait CommandMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ExecCommandFormats with sbt.protocol.codec.SettingQueryFormats =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.protocol.CommandMessage] = flatUnionFormat2[sbt.protocol.CommandMessage, sbt.protocol.ExecCommand, sbt.protocol.SettingQuery]("type")
}
