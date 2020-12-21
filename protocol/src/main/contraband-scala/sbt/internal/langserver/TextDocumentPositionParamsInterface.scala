/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Goto definition params model */
abstract class TextDocumentPositionParamsInterface(
  val textDocument: sbt.internal.langserver.TextDocumentIdentifier,
  val position: sbt.internal.langserver.Position) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: TextDocumentPositionParamsInterface => (this.textDocument == x.textDocument) && (this.position == x.position)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.TextDocumentPositionParamsInterface".##) + textDocument.##) + position.##)
  }
  override def toString: String = {
    "TextDocumentPositionParamsInterface(" + textDocument + ", " + position + ")"
  }
}
object TextDocumentPositionParamsInterface {
  
}
