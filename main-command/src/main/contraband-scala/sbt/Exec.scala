/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt
final class Exec private (
  val commandLine: String,
  val source: Option[sbt.CommandSource]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Exec => (this.commandLine == x.commandLine) && (this.source == x.source)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + commandLine.##) + source.##)
  }
  override def toString: String = {
    "Exec(" + commandLine + ", " + source + ")"
  }
  protected[this] def copy(commandLine: String = commandLine, source: Option[sbt.CommandSource] = source): Exec = {
    new Exec(commandLine, source)
  }
  def withCommandLine(commandLine: String): Exec = {
    copy(commandLine = commandLine)
  }
  def withSource(source: Option[sbt.CommandSource]): Exec = {
    copy(source = source)
  }
  def withSource(source: sbt.CommandSource): Exec = {
    copy(source = Option(source))
  }
}
object Exec {
  
  def apply(commandLine: String, source: Option[sbt.CommandSource]): Exec = new Exec(commandLine, source)
  def apply(commandLine: String, source: sbt.CommandSource): Exec = new Exec(commandLine, Option(source))
}
