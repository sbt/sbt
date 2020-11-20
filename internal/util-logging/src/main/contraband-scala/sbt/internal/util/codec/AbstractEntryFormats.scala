/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec

import _root_.sjsonnew.JsonFormat
trait AbstractEntryFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.util.codec.StringEventFormats with sbt.internal.util.codec.TraceEventFormats with sbt.internal.util.codec.ProgressItemFormats with sbt.internal.util.codec.ProgressEventFormats =>
implicit lazy val AbstractEntryFormat: JsonFormat[sbt.internal.util.AbstractEntry] = flatUnionFormat3[sbt.internal.util.AbstractEntry, sbt.internal.util.StringEvent, sbt.internal.util.TraceEvent, sbt.internal.util.ProgressEvent]("type")
}
