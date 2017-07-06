/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec

import _root_.sjsonnew.JsonFormat
trait TestMessageFormats { self: sjsonnew.BasicJsonProtocol with sbt.protocol.testing.codec.TestStringEventFormats with sbt.protocol.testing.codec.TestInitEventFormats with sbt.protocol.testing.codec.TestResultFormats with sbt.protocol.testing.codec.TestCompleteEventFormats with sbt.protocol.testing.codec.StartTestGroupEventFormats with sbt.protocol.testing.codec.EndTestGroupEventFormats with sbt.protocol.testing.codec.EndTestGroupErrorEventFormats with sbt.protocol.testing.codec.TestItemDetailFormats with sbt.protocol.testing.codec.TestItemEventFormats =>
implicit lazy val TestMessageFormat: JsonFormat[sbt.protocol.testing.TestMessage] = flatUnionFormat7[sbt.protocol.testing.TestMessage, sbt.protocol.testing.TestStringEvent, sbt.protocol.testing.TestInitEvent, sbt.protocol.testing.TestCompleteEvent, sbt.protocol.testing.StartTestGroupEvent, sbt.protocol.testing.EndTestGroupEvent, sbt.protocol.testing.EndTestGroupErrorEvent, sbt.protocol.testing.TestItemEvent]("type")
}
