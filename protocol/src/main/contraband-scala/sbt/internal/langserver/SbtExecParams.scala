/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/** Command to execute sbt command. */
final class SbtExecParams private (
  val commandLine: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SbtExecParams => (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.SbtExecParams".##) + commandLine.##)
  }
  override def toString: String = {
    "SbtExecParams(" + commandLine + ")"
  }
  protected[this] def copy(commandLine: String = commandLine): SbtExecParams = {
    new SbtExecParams(commandLine)
  }
  def withCommandLine(commandLine: String): SbtExecParams = {
    copy(commandLine = commandLine)
  }
}
object SbtExecParams {
  
  def apply(commandLine: String): SbtExecParams = new SbtExecParams(commandLine)
}
