/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Execution event. */
final class ExecutionEvent private (
  val success: String,
  val commandLine: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecutionEvent => (this.success == x.success) && (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.protocol.ExecutionEvent".##) + success.##) + commandLine.##)
  }
  override def toString: String = {
    "ExecutionEvent(" + success + ", " + commandLine + ")"
  }
  protected[this] def copy(success: String = success, commandLine: String = commandLine): ExecutionEvent = {
    new ExecutionEvent(success, commandLine)
  }
  def withSuccess(success: String): ExecutionEvent = {
    copy(success = success)
  }
  def withCommandLine(commandLine: String): ExecutionEvent = {
    copy(commandLine = commandLine)
  }
}
object ExecutionEvent {
  
  def apply(success: String, commandLine: String): ExecutionEvent = new ExecutionEvent(success, commandLine)
}
