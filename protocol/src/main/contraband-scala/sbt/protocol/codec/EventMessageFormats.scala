/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait EventMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.codec.LogEventFormats with sbt.protocol.codec.StatusEventFormats =>
implicit lazy val EventMessageFormat: JsonFormat[sbt.protocol.EventMessage] = flatUnionFormat2[sbt.protocol.EventMessage, sbt.protocol.LogEvent, sbt.protocol.StatusEvent]("type")
}
