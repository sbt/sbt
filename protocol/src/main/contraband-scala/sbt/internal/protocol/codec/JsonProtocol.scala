/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.protocol.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.protocol.codec.ExecCommandFormats
  with sbt.internal.protocol.codec.SettingQueryFormats
  with sbt.internal.protocol.codec.CommandMessageFormats
  with sbt.internal.protocol.codec.ChannelAcceptedEventFormats
  with sbt.internal.protocol.codec.LogEventFormats
  with sbt.internal.protocol.codec.ExecStatusEventFormats
  with sbt.internal.util.codec.JValueFormats
  with sbt.internal.protocol.codec.SettingQuerySuccessFormats
  with sbt.internal.protocol.codec.SettingQueryFailureFormats
  with sbt.internal.protocol.codec.EventMessageFormats
  with sbt.internal.protocol.codec.SettingQueryResponseFormats
  with sbt.internal.protocol.codec.ExecutionEventFormats
object JsonProtocol extends JsonProtocol