/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec

import _root_.sjsonnew.JsonFormat
trait TestMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.internal.protocol.testing.codec.TestStringEventFormats with sbt.internal.protocol.testing.codec.TestInitEventFormats with sbt.internal.protocol.testing.codec.TestResultFormats with sbt.internal.protocol.testing.codec.TestCompleteEventFormats with sbt.internal.protocol.testing.codec.StartTestGroupEventFormats with sbt.internal.protocol.testing.codec.EndTestGroupEventFormats with sbt.internal.protocol.testing.codec.EndTestGroupErrorEventFormats with sbt.internal.protocol.testing.codec.TestItemDetailFormats with sbt.internal.protocol.testing.codec.TestItemEventFormats =>
implicit lazy val TestMessageFormat: JsonFormat[sbt.internal.protocol.testing.TestMessage] = flatUnionFormat7[sbt.internal.protocol.testing.TestMessage, sbt.internal.protocol.testing.TestStringEvent, sbt.internal.protocol.testing.TestInitEvent, sbt.internal.protocol.testing.TestCompleteEvent, sbt.internal.protocol.testing.StartTestGroupEvent, sbt.internal.protocol.testing.EndTestGroupEvent, sbt.internal.protocol.testing.EndTestGroupErrorEvent, sbt.internal.protocol.testing.TestItemEvent]("type")
}
