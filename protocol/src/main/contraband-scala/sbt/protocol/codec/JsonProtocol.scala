/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.protocol.codec.InitCommandFormats
  with sbt.protocol.codec.ExecCommandFormats
  with sbt.protocol.codec.SettingQueryFormats
  with sbt.protocol.codec.AttachFormats
  with sbt.protocol.codec.TerminalCapabilitiesQueryFormats
  with sbt.protocol.codec.TerminalSetAttributesCommandFormats
  with sbt.protocol.codec.TerminalAttributesQueryFormats
  with sbt.protocol.codec.TerminalGetSizeQueryFormats
  with sbt.protocol.codec.TerminalSetSizeCommandFormats
  with sbt.protocol.codec.CommandMessageFormats
  with sbt.protocol.codec.CompletionParamsFormats
  with sbt.protocol.codec.ChannelAcceptedEventFormats
  with sbt.protocol.codec.LogEventFormats
  with sbt.protocol.codec.ExecStatusEventFormats
  with sbt.internal.util.codec.JValueFormats
  with sbt.protocol.codec.SettingQuerySuccessFormats
  with sbt.protocol.codec.SettingQueryFailureFormats
  with sbt.protocol.codec.TerminalPropertiesResponseFormats
  with sbt.protocol.codec.TerminalCapabilitiesResponseFormats
  with sbt.protocol.codec.TerminalSetAttributesResponseFormats
  with sbt.protocol.codec.TerminalAttributesResponseFormats
  with sbt.protocol.codec.TerminalGetSizeResponseFormats
  with sbt.protocol.codec.TerminalSetSizeResponseFormats
  with sbt.protocol.codec.EventMessageFormats
  with sbt.protocol.codec.SettingQueryResponseFormats
  with sbt.protocol.codec.CompletionResponseFormats
  with sbt.protocol.codec.ExecutionEventFormats
object JsonProtocol extends JsonProtocol