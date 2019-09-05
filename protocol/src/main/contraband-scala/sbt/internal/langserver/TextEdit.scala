/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
final class TextEdit private (
  val range: sbt.internal.langserver.Range,
  val newText: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: TextEdit => (this.range == x.range) && (this.newText == x.newText)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.TextEdit".##) + range.##) + newText.##)
  }
  override def toString: String = {
    "TextEdit(" + range + ", " + newText + ")"
  }
  private[this] def copy(range: sbt.internal.langserver.Range = range, newText: String = newText): TextEdit = {
    new TextEdit(range, newText)
  }
  def withRange(range: sbt.internal.langserver.Range): TextEdit = {
    copy(range = range)
  }
  def withNewText(newText: String): TextEdit = {
    copy(newText = newText)
  }
}
object TextEdit {
  
  def apply(range: sbt.internal.langserver.Range, newText: String): TextEdit = new TextEdit(range, newText)
}
