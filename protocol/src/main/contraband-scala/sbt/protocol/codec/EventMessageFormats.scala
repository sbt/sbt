/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec

import _root_.sjsonnew.JsonFormat
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ChannelAcceptedEventFormats with sbt.protocol.codec.LogEventFormats with sbt.protocol.codec.ExecStatusEventFormats with sbt.internal.util.codec.JValueFormats with sbt.protocol.codec.SettingQuerySuccessFormats with sbt.protocol.codec.SettingQueryFailureFormats with sbt.protocol.codec.TerminalPropertiesResponseFormats with sbt.protocol.codec.TerminalCapabilitiesResponseFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat7[sbt.protocol.EventMessage, sbt.protocol.ChannelAcceptedEvent, sbt.protocol.LogEvent, sbt.protocol.ExecStatusEvent, sbt.protocol.SettingQuerySuccess, sbt.protocol.SettingQueryFailure, sbt.protocol.TerminalPropertiesResponse, sbt.protocol.TerminalCapabilitiesResponse]("type")
}
