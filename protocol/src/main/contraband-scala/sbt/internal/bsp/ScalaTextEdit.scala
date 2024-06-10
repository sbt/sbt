/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * A textual edit applicable to a text document.
 * @param range The range of the text document to be manipulated. To insert
                text into a document create a range where start === end.
 * @param newText The string to be inserted. For delete operations use an
                  empty string.
 */
final class ScalaTextEdit private (
  val range: sbt.internal.bsp.Range,
  val newText: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaTextEdit => (this.range == x.range) && (this.newText == x.newText)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaTextEdit".##) + range.##) + newText.##)
  }
  override def toString: String = {
    "ScalaTextEdit(" + range + ", " + newText + ")"
  }
  private[this] def copy(range: sbt.internal.bsp.Range = range, newText: String = newText): ScalaTextEdit = {
    new ScalaTextEdit(range, newText)
  }
  def withRange(range: sbt.internal.bsp.Range): ScalaTextEdit = {
    copy(range = range)
  }
  def withNewText(newText: String): ScalaTextEdit = {
    copy(newText = newText)
  }
}
object ScalaTextEdit {
  
  def apply(range: sbt.internal.bsp.Range, newText: String): ScalaTextEdit = new ScalaTextEdit(range, newText)
}
