/**
 * This code is generated using sbt-datatype.
 */

// DO NOT EDIT MANUALLY
package sbt.protocol
/** Executon event. */
final class ExectionEvent private (
  val success: String,
  val commandLine: String) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ExectionEvent => (this.success == x.success) && (this.commandLine == x.commandLine)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + success.##) + commandLine.##)
  }
  override def toString: String = {
    "ExectionEvent(" + success + ", " + commandLine + ")"
  }
  protected[this] def copy(success: String = success, commandLine: String = commandLine): ExectionEvent = {
    new ExectionEvent(success, commandLine)
  }
  def withSuccess(success: String): ExectionEvent = {
    copy(success = success)
  }
  def withCommandLine(commandLine: String): ExectionEvent = {
    copy(commandLine = commandLine)
  }
}
object ExectionEvent {
  
  def apply(success: String, commandLine: String): ExectionEvent = new ExectionEvent(success, commandLine)
}
