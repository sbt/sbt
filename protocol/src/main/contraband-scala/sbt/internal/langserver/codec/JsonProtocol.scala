/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver.codec
trait JsonProtocol extends sjsonnew.BasicJsonProtocol
  with sbt.internal.langserver.codec.PositionFormats
  with sbt.internal.langserver.codec.RangeFormats
  with sbt.internal.langserver.codec.LocationFormats
  with sbt.internal.langserver.codec.DiagnosticFormats
  with sbt.internal.util.codec.JValueFormats
  with sbt.internal.langserver.codec.ClientCapabilitiesFormats
  with sbt.internal.langserver.codec.InitializeParamsFormats
  with sbt.internal.langserver.codec.SaveOptionsFormats
  with sbt.internal.langserver.codec.TextDocumentSyncOptionsFormats
  with sbt.internal.langserver.codec.ServerCapabilitiesFormats
  with sbt.internal.langserver.codec.InitializeResultFormats
  with sbt.internal.langserver.codec.PublishDiagnosticsParamsFormats
  with sbt.internal.langserver.codec.SbtExecParamsFormats
object JsonProtocol extends JsonProtocol