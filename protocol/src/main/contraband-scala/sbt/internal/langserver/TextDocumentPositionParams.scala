/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Goto definition params model */
final class TextDocumentPositionParams private (
  /** The text document. */
  val textDocument: sbt.internal.langserver.TextDocumentIdentifier,
  /** The position inside the text document. */
  val position: sbt.internal.langserver.Position) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TextDocumentPositionParams => (this.textDocument == x.textDocument) && (this.position == x.position)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.TextDocumentPositionParams".##) + textDocument.##) + position.##)
  }
  override def toString: String = {
    "TextDocumentPositionParams(" + textDocument + ", " + position + ")"
  }
  protected[this] def copy(textDocument: sbt.internal.langserver.TextDocumentIdentifier = textDocument, position: sbt.internal.langserver.Position = position): TextDocumentPositionParams = {
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
