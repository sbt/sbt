/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
import _root_.sjsonnew.{ deserializationError, serializationError, Builder, JsonFormat, Unbuilder }
trait AbstractEntryFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.util.codec.ChannelLogEntryFormats =>
implicit lazy val AbstractEntryFormat: JsonFormat[sbt.internal.util.AbstractEntry] = flatUnionFormat1[sbt.internal.util.AbstractEntry, sbt.internal.util.ChannelLogEntry]("type")
}
