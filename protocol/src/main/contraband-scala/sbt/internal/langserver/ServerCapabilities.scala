/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class ServerCapabilities private (
  val textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions],
  /** The server provides hover support. */
  val hoverProvider: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ServerCapabilities => (this.textDocumentSync == x.textDocumentSync) && (this.hoverProvider == x.hoverProvider)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.ServerCapabilities".##) + textDocumentSync.##) + hoverProvider.##)
  }
  override def toString: String = {
    "ServerCapabilities(" + textDocumentSync + ", " + hoverProvider + ")"
  }
  protected[this] def copy(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions] = textDocumentSync, hoverProvider: Option[Boolean] = hoverProvider): ServerCapabilities = {
    new ServerCapabilities(textDocumentSync, hoverProvider)
  }
  def withTextDocumentSync(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions]): ServerCapabilities = {
    copy(textDocumentSync = textDocumentSync)
  }
  def withTextDocumentSync(textDocumentSync: sbt.internal.langserver.TextDocumentSyncOptions): ServerCapabilities = {
    copy(textDocumentSync = Option(textDocumentSync))
  }
  def withHoverProvider(hoverProvider: Option[Boolean]): ServerCapabilities = {
    copy(hoverProvider = hoverProvider)
  }
  def withHoverProvider(hoverProvider: Boolean): ServerCapabilities = {
    copy(hoverProvider = Option(hoverProvider))
  }
}
object ServerCapabilities {
  
  def apply(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions], hoverProvider: Option[Boolean]): ServerCapabilities = new ServerCapabilities(textDocumentSync, hoverProvider)
  def apply(textDocumentSync: sbt.internal.langserver.TextDocumentSyncOptions, hoverProvider: Boolean): ServerCapabilities = new ServerCapabilities(Option(textDocumentSync), Option(hoverProvider))
}
