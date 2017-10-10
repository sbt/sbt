/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * Position in a text document expressed as zero-based line and zero-based character offset.
 * A position is between two characters like an 'insert' cursor in a editor.
 */
final class Position private (
  /** Line position in a document (zero-based). */
  val line: Long,
  /** Character offset on a line in a document (zero-based). */
  val character: Long) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Position => (this.line == x.line) && (this.character == x.character)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.langserver.Position".##) + line.##) + character.##)
  }
  override def toString: String = {
    "Position(" + line + ", " + character + ")"
  }
  protected[this] def copy(line: Long = line, character: Long = character): Position = {
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
