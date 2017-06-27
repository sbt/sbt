/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec

import _root_.sjsonnew.JsonFormat
trait AbstractEntryFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.util.codec.StringEventFormats with sbt.internal.util.codec.TraceEventFormats =>
implicit lazy val AbstractEntryFormat: JsonFormat[sbt.internal.util.AbstractEntry] = flatUnionFormat2[sbt.internal.util.AbstractEntry, sbt.internal.util.StringEvent, sbt.internal.util.TraceEvent]("type")
}
