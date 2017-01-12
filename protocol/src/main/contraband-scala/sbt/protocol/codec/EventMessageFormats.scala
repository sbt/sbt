/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.ChannelAcceptedEventFormats with sbt.protocol.codec.LogEventFormats with sbt.protocol.codec.ExecStatusEventFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat3[sbt.protocol.EventMessage, sbt.protocol.ChannelAcceptedEvent, sbt.protocol.LogEvent, sbt.protocol.ExecStatusEvent]("type")
}
