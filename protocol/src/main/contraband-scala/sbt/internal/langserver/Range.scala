/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * A range in a text document expressed as (zero-based) start and end positions. A range is comparable to a selection in an editor.
 * Therefore the end position is exclusive.
 */
final class Range private (
  /** The range's start position. */
  val start: sbt.internal.langserver.Position,
  /** The range's end position. */
  val end: sbt.internal.langserver.Position) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Range => (this.start == x.start) && (this.end == x.end)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.Range".##) + start.##) + end.##)
  }
  override def toString: String = {
    "Range(" + start + ", " + end + ")"
  }
  protected[this] def copy(start: sbt.internal.langserver.Position = start, end: sbt.internal.langserver.Position = end): Range = {
    new Range(start, end)
  }
  def withStart(start: sbt.internal.langserver.Position): Range = {
    copy(start = start)
  }
  def withEnd(end: sbt.internal.langserver.Position): Range = {
    copy(end = end)
  }
}
object Range {
  
  def apply(start: sbt.internal.langserver.Position, end: sbt.internal.langserver.Position): Range = new Range(start, end)
}
