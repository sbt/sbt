/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.testing.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.protocol.testing.codec.TestStringEventFormats
  with sbt.internal.protocol.testing.codec.TestInitEventFormats
  with sbt.internal.protocol.testing.codec.TestResultFormats
  with sbt.internal.protocol.testing.codec.TestCompleteEventFormats
  with sbt.internal.protocol.testing.codec.StartTestGroupEventFormats
  with sbt.internal.protocol.testing.codec.EndTestGroupEventFormats
  with sbt.internal.protocol.testing.codec.EndTestGroupErrorEventFormats
  with sbt.internal.testing.StatusFormats
  with sbt.internal.protocol.testing.codec.TestItemDetailFormats
  with sbt.internal.protocol.testing.codec.TestItemEventFormats
  with sbt.internal.protocol.testing.codec.TestMessageFormats
object JsonProtocol extends JsonProtocol