/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec

import _root_.sjsonnew.JsonFormat
trait AbstractEntryFormats { self: sjsonnew.BasicJsonProtocol & sbt.internal.util.codec.StringEventFormats & sbt.internal.util.codec.TraceEventFormats & sbt.internal.util.codec.ProgressItemFormats & sbt.internal.util.codec.ProgressEventFormats =>
implicit lazy val AbstractEntryFormat: JsonFormat[sbt.internal.util.AbstractEntry] = flatUnionFormat3[sbt.internal.util.AbstractEntry, sbt.internal.util.StringEvent, sbt.internal.util.TraceEvent, sbt.internal.util.ProgressEvent]("type")
}
