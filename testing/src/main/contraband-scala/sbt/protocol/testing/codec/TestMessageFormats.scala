/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec

import _root_.sjsonnew.JsonFormat
trait TestMessageFormats { self: sjsonnew.BasicJsonProtocol & sbt.protocol.testing.codec.TestStringEventFormats & sbt.protocol.testing.codec.TestInitEventFormats & sbt.protocol.testing.codec.TestResultFormats & sbt.protocol.testing.codec.TestCompleteEventFormats & sbt.protocol.testing.codec.StartTestGroupEventFormats & sbt.protocol.testing.codec.EndTestGroupEventFormats & sbt.protocol.testing.codec.EndTestGroupErrorEventFormats & sbt.protocol.testing.codec.TestItemDetailFormats & sbt.internal.testing.StatusFormats & sbt.protocol.testing.codec.TestItemEventFormats =>
implicit lazy val TestMessageFormat: JsonFormat[sbt.protocol.testing.TestMessage] = flatUnionFormat7[sbt.protocol.testing.TestMessage, sbt.protocol.testing.TestStringEvent, sbt.protocol.testing.TestInitEvent, sbt.protocol.testing.TestCompleteEvent, sbt.protocol.testing.StartTestGroupEvent, sbt.protocol.testing.EndTestGroupEvent, sbt.protocol.testing.EndTestGroupErrorEvent, sbt.protocol.testing.TestItemEvent]("type")
}
