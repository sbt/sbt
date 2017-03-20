/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ChannelAcceptedEventFormats with sbt.protocol.codec.LogEventFormats with sbt.protocol.codec.ExecStatusEventFormats with sbt.internal.JValueFormat with sbt.protocol.codec.SettingQueryResponseFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat4[sbt.protocol.EventMessage, sbt.protocol.ChannelAcceptedEvent, sbt.protocol.LogEvent, sbt.protocol.ExecStatusEvent, sbt.protocol.SettingQueryResponse]("type")
}
