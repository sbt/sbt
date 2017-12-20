/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Diagnostics notification are sent from the server to the client to signal results of validation runs. */
final class PublishDiagnosticsParams private (
  /** The URI for which diagnostic information is reported. */
  val uri: String,
  /** An array of diagnostic information items. */
  val diagnostics: Vector[sbt.internal.langserver.Diagnostic]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: PublishDiagnosticsParams => (this.uri == x.uri) && (this.diagnostics == x.diagnostics)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.PublishDiagnosticsParams".##) + uri.##) + diagnostics.##)
  }
  override def toString: String = {
    "PublishDiagnosticsParams(" + uri + ", " + diagnostics + ")"
  }
  protected[this] def copy(uri: String = uri, diagnostics: Vector[sbt.internal.langserver.Diagnostic] = diagnostics): PublishDiagnosticsParams = {
    new PublishDiagnosticsParams(uri, diagnostics)
  }
  def withUri(uri: String): PublishDiagnosticsParams = {
    copy(uri = uri)
  }
  def withDiagnostics(diagnostics: Vector[sbt.internal.langserver.Diagnostic]): PublishDiagnosticsParams = {
    copy(diagnostics = diagnostics)
  }
}
object PublishDiagnosticsParams {
  
  def apply(uri: String, diagnostics: Vector[sbt.internal.langserver.Diagnostic]): PublishDiagnosticsParams = new PublishDiagnosticsParams(uri, diagnostics)
}
