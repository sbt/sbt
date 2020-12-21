/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param uri The file's Uri */
final class TextDocumentIdentifier private (
  val uri: java.net.URI) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TextDocumentIdentifier => (this.uri == x.uri)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.TextDocumentIdentifier".##) + uri.##)
  }
  override def toString: String = {
    "TextDocumentIdentifier(" + uri + ")"
  }
  private[this] def copy(uri: java.net.URI = uri): TextDocumentIdentifier = {
    new TextDocumentIdentifier(uri)
  }
  def withUri(uri: java.net.URI): TextDocumentIdentifier = {
    copy(uri = uri)
  }
}
object TextDocumentIdentifier {
  
  def apply(uri: java.net.URI): TextDocumentIdentifier = new TextDocumentIdentifier(uri)
}
