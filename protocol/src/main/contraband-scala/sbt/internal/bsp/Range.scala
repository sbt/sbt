/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * A range in a text document expressed as (zero-based) start and end positions. A range is comparable to a selection in an editor.
 * Therefore the end position is exclusive.
 * @param start The range's start position.
 * @param end The range's end position.
 */
final class Range private (
  val start: sbt.internal.bsp.Position,
  val end: sbt.internal.bsp.Position) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Range => (this.start == x.start) && (this.end == x.end)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.Range".##) + start.##) + end.##)
  }
  override def toString: String = {
    "Range(" + start + ", " + end + ")"
  }
  private[this] def copy(start: sbt.internal.bsp.Position = start, end: sbt.internal.bsp.Position = end): Range = {
    new Range(start, end)
  }
  def withStart(start: sbt.internal.bsp.Position): Range = {
    copy(start = start)
  }
  def withEnd(end: sbt.internal.bsp.Position): Range = {
    copy(end = end)
  }
}
object Range {
  
  def apply(start: sbt.internal.bsp.Position, end: sbt.internal.bsp.Position): Range = new Range(start, end)
}
