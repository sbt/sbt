/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.util.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.util.codec.StringEventFormats
  with sbt.internal.util.codec.TraceEventFormats
  with sbt.internal.util.codec.AbstractEntryFormats
  with sbt.internal.util.codec.SuccessEventFormats
object JsonProtocol extends JsonProtocol