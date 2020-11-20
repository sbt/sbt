/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class CompletionList private (
  val isIncomplete: Boolean,
  val items: Vector[sbt.internal.langserver.CompletionItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionList => (this.isIncomplete == x.isIncomplete) && (this.items == x.items)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.CompletionList".##) + isIncomplete.##) + items.##)
  }
  override def toString: String = {
    "CompletionList(" + isIncomplete + ", " + items + ")"
  }
  private[this] def copy(isIncomplete: Boolean = isIncomplete, items: Vector[sbt.internal.langserver.CompletionItem] = items): CompletionList = {
    new CompletionList(isIncomplete, items)
  }
  def withIsIncomplete(isIncomplete: Boolean): CompletionList = {
    copy(isIncomplete = isIncomplete)
  }
  def withItems(items: Vector[sbt.internal.langserver.CompletionItem]): CompletionList = {
    copy(items = items)
  }
}
object CompletionList {
  
  def apply(isIncomplete: Boolean, items: Vector[sbt.internal.langserver.CompletionItem]): CompletionList = new CompletionList(isIncomplete, items)
}
