/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Position in a text document expressed as zero-based line and zero-based character offset.
 * A position is between two characters like an 'insert' cursor in a editor.
 * @param line Line position in a document (zero-based).
 * @param character Character offset on a line in a document (zero-based).
 */
final class Position private (
  val line: Long,
  val character: Long) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: Position => (this.line == x.line) && (this.character == x.character)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.Position".##) + line.##) + character.##)
  }
  override def toString: String = {
    "Position(" + line + ", " + character + ")"
  }
  private[this] def copy(line: Long = line, character: Long = character): Position = {
    new Position(line, character)
  }
  def withLine(line: Long): Position = {
    copy(line = line)
  }
  def withCharacter(character: Long): Position = {
    copy(character = character)
  }
}
object Position {
  
  def apply(line: Long, character: Long): Position = new Position(line, character)
}
