/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol & sbt.protocol.codec.ChannelAcceptedEventFormats & sbt.protocol.codec.LogEventFormats & sbt.protocol.codec.ExecStatusEventFormats & sbt.internal.util.codec.JValueFormats & sbt.protocol.codec.SettingQuerySuccessFormats & sbt.protocol.codec.SettingQueryFailureFormats & sbt.protocol.codec.TerminalPropertiesResponseFormats & sbt.protocol.codec.TerminalCapabilitiesResponseFormats & sbt.protocol.codec.TerminalSetAttributesResponseFormats & sbt.protocol.codec.TerminalAttributesResponseFormats & sbt.protocol.codec.TerminalGetSizeResponseFormats & sbt.protocol.codec.TerminalSetSizeResponseFormats & sbt.protocol.codec.TerminalSetEchoResponseFormats & sbt.protocol.codec.TerminalSetRawModeResponseFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat13[sbt.protocol.EventMessage, sbt.protocol.ChannelAcceptedEvent, sbt.protocol.LogEvent, sbt.protocol.ExecStatusEvent, sbt.protocol.SettingQuerySuccess, sbt.protocol.SettingQueryFailure, sbt.protocol.TerminalPropertiesResponse, sbt.protocol.TerminalCapabilitiesResponse, sbt.protocol.TerminalSetAttributesResponse, sbt.protocol.TerminalAttributesResponse, sbt.protocol.TerminalGetSizeResponse, sbt.protocol.TerminalSetSizeResponse, sbt.protocol.TerminalSetEchoResponse, sbt.protocol.TerminalSetRawModeResponse]("type")
}
