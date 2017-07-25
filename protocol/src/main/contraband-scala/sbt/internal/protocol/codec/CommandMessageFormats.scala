/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec

import _root_.sjsonnew.JsonFormat
trait CommandMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.protocol.codec.ExecCommandFormats with sbt.internal.protocol.codec.SettingQueryFormats =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.internal.protocol.CommandMessage] = flatUnionFormat2[sbt.internal.protocol.CommandMessage, sbt.internal.protocol.ExecCommand, sbt.internal.protocol.SettingQuery]("type")
}
