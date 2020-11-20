/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Publish Diagnostics
 * @param textDocument The document where the diagnostics are published.
 * @param buildTarget The build target where the diagnostics origin.
                      It is valid for one text to belong to multiple build targets,
                      for example sources that are compiled against multiple platforms (JVM, JavaScript).
 * @param originId The request id that originated this notification
 * @param diagnostics The diagnostics to be published by the client
 * @param reset Whether the client should clear the previous diagnostics
                mapped to the same `textDocument` and buildTarget
 */
final class PublishDiagnosticsParams private (
  val textDocument: sbt.internal.bsp.TextDocumentIdentifier,
  val buildTarget: sbt.internal.bsp.BuildTargetIdentifier,
  val originId: Option[String],
  val diagnostics: Vector[sbt.internal.bsp.Diagnostic],
  val reset: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: PublishDiagnosticsParams => (this.textDocument == x.textDocument) && (this.buildTarget == x.buildTarget) && (this.originId == x.originId) && (this.diagnostics == x.diagnostics) && (this.reset == x.reset)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.PublishDiagnosticsParams".##) + textDocument.##) + buildTarget.##) + originId.##) + diagnostics.##) + reset.##)
  }
  override def toString: String = {
    "PublishDiagnosticsParams(" + textDocument + ", " + buildTarget + ", " + originId + ", " + diagnostics + ", " + reset + ")"
  }
  private[this] def copy(textDocument: sbt.internal.bsp.TextDocumentIdentifier = textDocument, buildTarget: sbt.internal.bsp.BuildTargetIdentifier = buildTarget, originId: Option[String] = originId, diagnostics: Vector[sbt.internal.bsp.Diagnostic] = diagnostics, reset: Boolean = reset): PublishDiagnosticsParams = {
    new PublishDiagnosticsParams(textDocument, buildTarget, originId, diagnostics, reset)
  }
  def withTextDocument(textDocument: sbt.internal.bsp.TextDocumentIdentifier): PublishDiagnosticsParams = {
    copy(textDocument = textDocument)
  }
  def withBuildTarget(buildTarget: sbt.internal.bsp.BuildTargetIdentifier): PublishDiagnosticsParams = {
    copy(buildTarget = buildTarget)
  }
  def withOriginId(originId: Option[String]): PublishDiagnosticsParams = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): PublishDiagnosticsParams = {
    copy(originId = Option(originId))
  }
  def withDiagnostics(diagnostics: Vector[sbt.internal.bsp.Diagnostic]): PublishDiagnosticsParams = {
    copy(diagnostics = diagnostics)
  }
  def withReset(reset: Boolean): PublishDiagnosticsParams = {
    copy(reset = reset)
  }
}
object PublishDiagnosticsParams {
  
  def apply(textDocument: sbt.internal.bsp.TextDocumentIdentifier, buildTarget: sbt.internal.bsp.BuildTargetIdentifier, originId: Option[String], diagnostics: Vector[sbt.internal.bsp.Diagnostic], reset: Boolean): PublishDiagnosticsParams = new PublishDiagnosticsParams(textDocument, buildTarget, originId, diagnostics, reset)
  def apply(textDocument: sbt.internal.bsp.TextDocumentIdentifier, buildTarget: sbt.internal.bsp.BuildTargetIdentifier, originId: String, diagnostics: Vector[sbt.internal.bsp.Diagnostic], reset: Boolean): PublishDiagnosticsParams = new PublishDiagnosticsParams(textDocument, buildTarget, Option(originId), diagnostics, reset)
}
