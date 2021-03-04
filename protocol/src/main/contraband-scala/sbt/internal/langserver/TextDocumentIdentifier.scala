/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * Text documents are identified using a URI. On the protocol level, URIs are passed as strings.
 * @param uri The text document's URI.
 */
final class TextDocumentIdentifier private (
  val uri: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TextDocumentIdentifier => (this.uri == x.uri)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.TextDocumentIdentifier".##) + uri.##)
  }
  override def toString: String = {
    "TextDocumentIdentifier(" + uri + ")"
  }
  private[this] def copy(uri: String = uri): TextDocumentIdentifier = {
    new TextDocumentIdentifier(uri)
  }
  def withUri(uri: String): TextDocumentIdentifier = {
    copy(uri = uri)
  }
}
object TextDocumentIdentifier {
  
  def apply(uri: String): TextDocumentIdentifier = new TextDocumentIdentifier(uri)
}
