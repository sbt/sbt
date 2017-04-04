/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.testing.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.protocol.testing.codec.TestStringEventFormats
  with sbt.protocol.testing.codec.TestInitEventFormats
  with sbt.protocol.testing.codec.TestResultFormats
  with sbt.protocol.testing.codec.TestCompleteEventFormats
  with sbt.protocol.testing.codec.StartTestGroupEventFormats
  with sbt.protocol.testing.codec.EndTestGroupEventFormats
  with sbt.protocol.testing.codec.EndTestGroupErrorEventFormats
  with sbt.internal.testing.StatusFormats
  with sbt.protocol.testing.codec.TestItemDetailFormats
  with sbt.protocol.testing.codec.TestItemEventFormats
  with sbt.protocol.testing.codec.TestMessageFormats
object JsonProtocol extends JsonProtocol