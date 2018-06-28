/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class ServerCapabilities private (
  val textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions],
  /** The server provides hover support. */
  val hoverProvider: Option[Boolean],
  /** Goto definition */
  val definitionProvider: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ServerCapabilities => (this.textDocumentSync == x.textDocumentSync) && (this.hoverProvider == x.hoverProvider) && (this.definitionProvider == x.definitionProvider)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.ServerCapabilities".##) + textDocumentSync.##) + hoverProvider.##) + definitionProvider.##)
  }
  override def toString: String = {
    "ServerCapabilities(" + textDocumentSync + ", " + hoverProvider + ", " + definitionProvider + ")"
  }
  private[this] def copy(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions] = textDocumentSync, hoverProvider: Option[Boolean] = hoverProvider, definitionProvider: Option[Boolean] = definitionProvider): ServerCapabilities = {
    new ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider)
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
  def withDefinitionProvider(definitionProvider: Option[Boolean]): ServerCapabilities = {
    copy(definitionProvider = definitionProvider)
  }
  def withDefinitionProvider(definitionProvider: Boolean): ServerCapabilities = {
    copy(definitionProvider = Option(definitionProvider))
  }
}
object ServerCapabilities {
  
  def apply(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions], hoverProvider: Option[Boolean], definitionProvider: Option[Boolean]): ServerCapabilities = new ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider)
  def apply(textDocumentSync: sbt.internal.langserver.TextDocumentSyncOptions, hoverProvider: Boolean, definitionProvider: Boolean): ServerCapabilities = new ServerCapabilities(Option(textDocumentSync), Option(hoverProvider), Option(definitionProvider))
}
