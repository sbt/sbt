/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * Completion request interfaces
 * @param textDocument The text document.
 * @param position The position inside the text document.
 * @param context completion context
 */
final class CompletionParams private (
  textDocument: sbt.internal.langserver.TextDocumentIdentifier,
  position: sbt.internal.langserver.Position,
  val context: Option[sbt.internal.langserver.CompletionContext]) extends sbt.internal.langserver.TextDocumentPositionParamsInterface(textDocument, position) with Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionParams => (this.textDocument == x.textDocument) && (this.position == x.position) && (this.context == x.context)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.CompletionParams".##) + textDocument.##) + position.##) + context.##)
  }
  override def toString: String = {
    "CompletionParams(" + textDocument + ", " + position + ", " + context + ")"
  }
  private[this] def copy(textDocument: sbt.internal.langserver.TextDocumentIdentifier = textDocument, position: sbt.internal.langserver.Position = position, context: Option[sbt.internal.langserver.CompletionContext] = context): CompletionParams = {
    new CompletionParams(textDocument, position, context)
  }
  def withTextDocument(textDocument: sbt.internal.langserver.TextDocumentIdentifier): CompletionParams = {
    copy(textDocument = textDocument)
  }
  def withPosition(position: sbt.internal.langserver.Position): CompletionParams = {
    copy(position = position)
  }
  def withContext(context: Option[sbt.internal.langserver.CompletionContext]): CompletionParams = {
    copy(context = context)
  }
  def withContext(context: sbt.internal.langserver.CompletionContext): CompletionParams = {
    copy(context = Option(context))
  }
}
object CompletionParams {
  
  def apply(textDocument: sbt.internal.langserver.TextDocumentIdentifier, position: sbt.internal.langserver.Position, context: Option[sbt.internal.langserver.CompletionContext]): CompletionParams = new CompletionParams(textDocument, position, context)
  def apply(textDocument: sbt.internal.langserver.TextDocumentIdentifier, position: sbt.internal.langserver.Position, context: sbt.internal.langserver.CompletionContext): CompletionParams = new CompletionParams(textDocument, position, Option(context))
}
