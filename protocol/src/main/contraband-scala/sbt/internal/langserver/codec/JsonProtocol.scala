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
  with sbt.internal.langserver.codec.LogMessageParamsFormats
  with sbt.internal.langserver.codec.PublishDiagnosticsParamsFormats
  with sbt.internal.langserver.codec.SbtExecParamsFormats
  with sbt.internal.langserver.codec.CancelRequestParamsFormats
  with sbt.internal.langserver.codec.TextDocumentIdentifierFormats
  with sbt.internal.langserver.codec.TextDocumentPositionParamsFormats
  with sbt.internal.langserver.codec.CompletionContextFormats
  with sbt.internal.langserver.codec.CompletionParamsFormats
  with sbt.internal.langserver.codec.TextEditFormats
  with sbt.internal.langserver.codec.CommandFormats
  with sbt.internal.langserver.codec.CompletionItemFormats
  with sbt.internal.langserver.codec.CompletionListFormats
object JsonProtocol extends JsonProtocol