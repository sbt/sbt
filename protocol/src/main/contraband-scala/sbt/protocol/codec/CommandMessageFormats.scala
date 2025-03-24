/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait CommandMessageFormats { self: sbt.internal.protocol.codec.InitializeOptionFormats & sjsonnew.BasicJsonProtocol & sbt.protocol.codec.InitCommandFormats & sbt.protocol.codec.ExecCommandFormats & sbt.protocol.codec.SettingQueryFormats & sbt.protocol.codec.AttachFormats & sbt.protocol.codec.TerminalCapabilitiesQueryFormats & sbt.protocol.codec.TerminalSetAttributesCommandFormats & sbt.protocol.codec.TerminalAttributesQueryFormats & sbt.protocol.codec.TerminalGetSizeQueryFormats & sbt.protocol.codec.TerminalSetSizeCommandFormats & sbt.protocol.codec.TerminalSetEchoCommandFormats & sbt.protocol.codec.TerminalSetRawModeCommandFormats =>
implicit lazy val CommandMessageFormat: JsonFormat[sbt.protocol.CommandMessage] = flatUnionFormat11[sbt.protocol.CommandMessage, sbt.protocol.InitCommand, sbt.protocol.ExecCommand, sbt.protocol.SettingQuery, sbt.protocol.Attach, sbt.protocol.TerminalCapabilitiesQuery, sbt.protocol.TerminalSetAttributesCommand, sbt.protocol.TerminalAttributesQuery, sbt.protocol.TerminalGetSizeQuery, sbt.protocol.TerminalSetSizeCommand, sbt.protocol.TerminalSetEchoCommand, sbt.protocol.TerminalSetRawModeCommand]("type")
}
