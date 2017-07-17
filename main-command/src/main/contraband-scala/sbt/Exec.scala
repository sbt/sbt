/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt
final class Exec private (
  val commandLine: String,
  val execId: Option[String],
  val source: Option[sbt.CommandSource]) extends Serializable {
  
  private def this(commandLine: String, source: Option[sbt.CommandSource]) = this(commandLine, None, source)
  
  override def equals(o: Any): Boolean = o match {
    case x: Exec => (this.commandLine == x.commandLine) && (this.execId == x.execId) && (this.source == x.source)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.Exec".##) + commandLine.##) + execId.##) + source.##)
  }
  override def toString: String = {
    "Exec(" + commandLine + ", " + execId + ", " + source + ")"
  }
  protected[this] def copy(commandLine: String = commandLine, execId: Option[String] = execId, source: Option[sbt.CommandSource] = source): Exec = {
    new Exec(commandLine, execId, source)
  }
  def withCommandLine(commandLine: String): Exec = {
    copy(commandLine = commandLine)
  }
  def withExecId(execId: Option[String]): Exec = {
    copy(execId = execId)
  }
  def withExecId(execId: String): Exec = {
    copy(execId = Option(execId))
  }
  def withSource(source: Option[sbt.CommandSource]): Exec = {
    copy(source = source)
  }
  def withSource(source: sbt.CommandSource): Exec = {
    copy(source = Option(source))
  }
}
object Exec {
  def newExecId: String = java.util.UUID.randomUUID.toString
  def apply(commandLine: String, source: Option[sbt.CommandSource]): Exec = new Exec(commandLine, None, source)
  def apply(commandLine: String, source: sbt.CommandSource): Exec = new Exec(commandLine, None, Option(source))
  def apply(commandLine: String, execId: Option[String], source: Option[sbt.CommandSource]): Exec = new Exec(commandLine, execId, source)
  def apply(commandLine: String, execId: String, source: sbt.CommandSource): Exec = new Exec(commandLine, Option(execId), Option(source))
}
