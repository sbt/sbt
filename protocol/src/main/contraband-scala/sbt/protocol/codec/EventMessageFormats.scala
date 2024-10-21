/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ChannelAcceptedEventFormats with sbt.protocol.codec.LogEventFormats with sbt.protocol.codec.ExecStatusEventFormats with sbt.internal.util.codec.JValueFormats with sbt.protocol.codec.SettingQuerySuccessFormats with sbt.protocol.codec.SettingQueryFailureFormats with sbt.protocol.codec.TerminalPropertiesResponseFormats with sbt.protocol.codec.TerminalCapabilitiesResponseFormats with sbt.protocol.codec.TerminalSetAttributesResponseFormats with sbt.protocol.codec.TerminalAttributesResponseFormats with sbt.protocol.codec.TerminalGetSizeResponseFormats with sbt.protocol.codec.TerminalSetSizeResponseFormats with sbt.protocol.codec.TerminalSetEchoResponseFormats with sbt.protocol.codec.TerminalSetRawModeResponseFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat13[sbt.protocol.EventMessage, sbt.protocol.ChannelAcceptedEvent, sbt.protocol.LogEvent, sbt.protocol.ExecStatusEvent, sbt.protocol.SettingQuerySuccess, sbt.protocol.SettingQueryFailure, sbt.protocol.TerminalPropertiesResponse, sbt.protocol.TerminalCapabilitiesResponse, sbt.protocol.TerminalSetAttributesResponse, sbt.protocol.TerminalAttributesResponse, sbt.protocol.TerminalGetSizeResponse, sbt.protocol.TerminalSetSizeResponse, sbt.protocol.TerminalSetEchoResponse, sbt.protocol.TerminalSetRawModeResponse]("type")
}
