/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.worker.codec.HashedPathFormats
  with sbt.internal.worker.codec.GeneralParamsFormats
  with sbt.internal.worker.codec.ConsoleNotificationFormats
object JsonProtocol extends JsonProtocol