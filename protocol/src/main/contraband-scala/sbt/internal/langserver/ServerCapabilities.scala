/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * @param hoverProvider The server provides hover support.
 * @param definitionProvider Goto definition
 * @param successLog The server writes the result line itself, so a client should not print its own.
 * @param shutdownRequest The server accepts the sbt/shutdown request.
 */
final class ServerCapabilities private (
  val textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions],
  val hoverProvider: Option[Boolean],
  val definitionProvider: Option[Boolean],
  val successLog: Option[Boolean],
  val shutdownRequest: Option[Boolean]) extends Serializable {
  
  private def this(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions], hoverProvider: Option[Boolean], definitionProvider: Option[Boolean]) = this(textDocumentSync, hoverProvider, definitionProvider, None, None)
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ServerCapabilities => (this.textDocumentSync == x.textDocumentSync) && (this.hoverProvider == x.hoverProvider) && (this.definitionProvider == x.definitionProvider) && (this.successLog == x.successLog) && (this.shutdownRequest == x.shutdownRequest)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.ServerCapabilities".##) + textDocumentSync.##) + hoverProvider.##) + definitionProvider.##) + successLog.##) + shutdownRequest.##)
  }
  override def toString: String = {
    "ServerCapabilities(" + textDocumentSync + ", " + hoverProvider + ", " + definitionProvider + ", " + successLog + ", " + shutdownRequest + ")"
  }
  private def copy(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions] = textDocumentSync, hoverProvider: Option[Boolean] = hoverProvider, definitionProvider: Option[Boolean] = definitionProvider, successLog: Option[Boolean] = successLog, shutdownRequest: Option[Boolean] = shutdownRequest): ServerCapabilities = {
    new ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider, successLog, shutdownRequest)
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
  def withSuccessLog(successLog: Option[Boolean]): ServerCapabilities = {
    copy(successLog = successLog)
  }
  def withSuccessLog(successLog: Boolean): ServerCapabilities = {
    copy(successLog = Option(successLog))
  }
  def withShutdownRequest(shutdownRequest: Option[Boolean]): ServerCapabilities = {
    copy(shutdownRequest = shutdownRequest)
  }
  def withShutdownRequest(shutdownRequest: Boolean): ServerCapabilities = {
    copy(shutdownRequest = Option(shutdownRequest))
  }
}
object ServerCapabilities {
  
  def apply(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions], hoverProvider: Option[Boolean], definitionProvider: Option[Boolean]): ServerCapabilities = new ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider)
  def apply(textDocumentSync: sbt.internal.langserver.TextDocumentSyncOptions, hoverProvider: Boolean, definitionProvider: Boolean): ServerCapabilities = new ServerCapabilities(Option(textDocumentSync), Option(hoverProvider), Option(definitionProvider))
  def apply(textDocumentSync: Option[sbt.internal.langserver.TextDocumentSyncOptions], hoverProvider: Option[Boolean], definitionProvider: Option[Boolean], successLog: Option[Boolean], shutdownRequest: Option[Boolean]): ServerCapabilities = new ServerCapabilities(textDocumentSync, hoverProvider, definitionProvider, successLog, shutdownRequest)
  def apply(textDocumentSync: sbt.internal.langserver.TextDocumentSyncOptions, hoverProvider: Boolean, definitionProvider: Boolean, successLog: Boolean, shutdownRequest: Boolean): ServerCapabilities = new ServerCapabilities(Option(textDocumentSync), Option(hoverProvider), Option(definitionProvider), Option(successLog), Option(shutdownRequest))
}
