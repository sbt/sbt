/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class CompletionItem private (
  val label: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: CompletionItem => (this.label == x.label)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.CompletionItem".##) + label.##)
  }
  override def toString: String = {
    "CompletionItem(" + label + ")"
  }
  private[this] def copy(label: String = label): CompletionItem = {
    new CompletionItem(label)
  }
  def withLabel(label: String): CompletionItem = {
    copy(label = label)
  }
}
object CompletionItem {
  
  def apply(label: String): CompletionItem = new CompletionItem(label)
}
