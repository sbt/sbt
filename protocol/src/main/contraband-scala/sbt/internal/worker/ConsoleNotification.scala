/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
final class ConsoleNotification private (
  val ref: String,
  val stdout: Option[String],
  val stderr: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ConsoleNotification => (this.ref == x.ref) && (this.stdout == x.stdout) && (this.stderr == x.stderr)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.ConsoleNotification".##) + ref.##) + stdout.##) + stderr.##)
  }
  override def toString: String = {
    "ConsoleNotification(" + ref + ", " + stdout + ", " + stderr + ")"
  }
  private[this] def copy(ref: String = ref, stdout: Option[String] = stdout, stderr: Option[String] = stderr): ConsoleNotification = {
    new ConsoleNotification(ref, stdout, stderr)
  }
  def withRef(ref: String): ConsoleNotification = {
    copy(ref = ref)
  }
  def withStdout(stdout: Option[String]): ConsoleNotification = {
    copy(stdout = stdout)
  }
  def withStdout(stdout: String): ConsoleNotification = {
    copy(stdout = Option(stdout))
  }
  def withStderr(stderr: Option[String]): ConsoleNotification = {
    copy(stderr = stderr)
  }
  def withStderr(stderr: String): ConsoleNotification = {
    copy(stderr = Option(stderr))
  }
}
object ConsoleNotification {
  
  def apply(ref: String, stdout: Option[String], stderr: Option[String]): ConsoleNotification = new ConsoleNotification(ref, stdout, stderr)
  def apply(ref: String, stdout: String, stderr: String): ConsoleNotification = new ConsoleNotification(ref, Option(stdout), Option(stderr))
}
