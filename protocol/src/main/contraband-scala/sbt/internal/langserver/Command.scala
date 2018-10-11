/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
abstract class Command(
  val title: Option[String],
  val command: Option[String],
  val arguments: Vector[String]) extends Serializable {
  
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: Command => (this.title == x.title) && (this.command == x.command) && (this.arguments == x.arguments)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.langserver.Command".##) + title.##) + command.##) + arguments.##)
  }
  override def toString: String = {
    "Command(" + title + ", " + command + ", " + arguments + ")"
  }
}
object Command {
  
}
