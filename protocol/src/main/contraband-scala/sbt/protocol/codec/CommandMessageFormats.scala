/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait CommandMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.InitCommandFormats with sbt.protocol.codec.ExecCommandFormats with sbt.protocol.codec.SettingQueryFormats with sbt.protocol.codec.AttachFormats with sbt.protocol.codec.TerminalCapabilitiesQueryFormats with sbt.protocol.codec.TerminalSetAttributesCommandFormats with sbt.protocol.codec.TerminalAttributesQueryFormats with sbt.protocol.codec.TerminalGetSizeQueryFormats with sbt.protocol.codec.TerminalSetSizeCommandFormats with sbt.protocol.codec.TerminalSetEchoCommandFormats with sbt.protocol.codec.TerminalSetRawModeCommandFormats =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.protocol.CommandMessage] = flatUnionFormat11[sbt.protocol.CommandMessage, sbt.protocol.InitCommand, sbt.protocol.ExecCommand, sbt.protocol.SettingQuery, sbt.protocol.Attach, sbt.protocol.TerminalCapabilitiesQuery, sbt.protocol.TerminalSetAttributesCommand, sbt.protocol.TerminalAttributesQuery, sbt.protocol.TerminalGetSizeQuery, sbt.protocol.TerminalSetSizeCommand, sbt.protocol.TerminalSetEchoCommand, sbt.protocol.TerminalSetRawModeCommand]("type")
}
