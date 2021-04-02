/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * @param textDocument The text document.
 * @param position The position inside the text document.
 */
final class TextDocumentPositionParams private (
  textDocument: sbt.internal.langserver.TextDocumentIdentifier,
  position: sbt.internal.langserver.Position) extends sbt.internal.langserver.TextDocumentPositionParamsInterface(textDocument, position) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TextDocumentPositionParams => (this.textDocument == x.textDocument) && (this.position == x.position)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.TextDocumentPositionParams".##) + textDocument.##) + position.##)
  }
  override def toString: String = {
    "TextDocumentPositionParams(" + textDocument + ", " + position + ")"
  }
  private[this] def copy(textDocument: sbt.internal.langserver.TextDocumentIdentifier = textDocument, position: sbt.internal.langserver.Position = position): TextDocumentPositionParams = {
    new TextDocumentPositionParams(textDocument, position)
  }
  def withTextDocument(textDocument: sbt.internal.langserver.TextDocumentIdentifier): TextDocumentPositionParams = {
    copy(textDocument = textDocument)
  }
  def withPosition(position: sbt.internal.langserver.Position): TextDocumentPositionParams = {
    copy(position = position)
  }
}
object TextDocumentPositionParams {
  
  def apply(textDocument: sbt.internal.langserver.TextDocumentIdentifier, position: sbt.internal.langserver.Position): TextDocumentPositionParams = new TextDocumentPositionParams(textDocument, position)
}
