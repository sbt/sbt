/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Execution event. */
final class ExecutionEvent private (
  val success: String,
  val commandLine: String,
  val foo: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExecutionEvent => (this.success == x.success) && (this.commandLine == x.commandLine) && (this.foo == x.foo)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.protocol.ExecutionEvent".##) + success.##) + commandLine.##) + foo.##)
  }
  override def toString: String = {
    "ExecutionEvent(" + success + ", " + commandLine + ", " + foo + ")"
  }
  private[this] def copy(success: String = success, commandLine: String = commandLine, foo: Option[String] = foo): ExecutionEvent = {
    new ExecutionEvent(success, commandLine, foo)
  }
  def withSuccess(success: String): ExecutionEvent = {
    copy(success = success)
  }
  def withCommandLine(commandLine: String): ExecutionEvent = {
    copy(commandLine = commandLine)
  }
  def withFoo(foo: Option[String]): ExecutionEvent = {
    copy(foo = foo)
  }
  def withFoo(foo: String): ExecutionEvent = {
    copy(foo = Option(foo))
  }
}
object ExecutionEvent {
  
  def apply(success: String, commandLine: String, foo: Option[String]): ExecutionEvent = new ExecutionEvent(success, commandLine, foo)
  def apply(success: String, commandLine: String, foo: String): ExecutionEvent = new ExecutionEvent(success, commandLine, Option(foo))
}
