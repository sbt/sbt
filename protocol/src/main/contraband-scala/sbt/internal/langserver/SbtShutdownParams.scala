/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.langserver
/**
 * Asks the server to shut down. Without force, the shutdown waits for the running command.
 * @param force Cancel the running command and drop the queued ones instead of waiting for them.
 */
final class SbtShutdownParams private (
  val force: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: SbtShutdownParams => (this.force == x.force)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.langserver.SbtShutdownParams".##) + force.##)
  }
  override def toString: String = {
    "SbtShutdownParams(" + force + ")"
  }
  private def copy(force: Option[Boolean]): SbtShutdownParams = {
    new SbtShutdownParams(force)
  }
  def withForce(force: Option[Boolean]): SbtShutdownParams = {
    copy(force = force)
  }
  def withForce(force: Boolean): SbtShutdownParams = {
    copy(force = Option(force))
  }
}
object SbtShutdownParams {
  
  def apply(force: Option[Boolean]): SbtShutdownParams = new SbtShutdownParams(force)
  def apply(force: Boolean): SbtShutdownParams = new SbtShutdownParams(Option(force))
}
