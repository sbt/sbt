/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec

import _root_.sjsonnew.JsonFormat
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.protocol.codec.ChannelAcceptedEventFormats with sbt.internal.protocol.codec.LogEventFormats with sbt.internal.protocol.codec.ExecStatusEventFormats with sbt.internal.util.codec.JValueFormats with sbt.internal.protocol.codec.SettingQuerySuccessFormats with sbt.internal.protocol.codec.SettingQueryFailureFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.internal.protocol.EventMessage] = flatUnionFormat5[sbt.internal.protocol.EventMessage, sbt.internal.protocol.ChannelAcceptedEvent, sbt.internal.protocol.LogEvent, sbt.internal.protocol.ExecStatusEvent, sbt.internal.protocol.SettingQuerySuccess, sbt.internal.protocol.SettingQueryFailure]("type")
}
