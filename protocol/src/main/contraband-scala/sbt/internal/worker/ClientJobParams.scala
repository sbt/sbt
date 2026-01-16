/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
/**
 * Client-side job support.
 * 
 * Notification: sbt/clientJob
 * 
 * Parameter for the sbt/clientJob notification.
 * A client-side job represents a unit of work that sbt server
 * can outsourse back to the client, for example for run task.
 */
final class ClientJobParams private (
  val runInfo: Option[sbt.internal.worker.RunInfo]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ClientJobParams => (this.runInfo == x.runInfo)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.worker.ClientJobParams".##) + runInfo.##)
  }
  override def toString: String = {
    "ClientJobParams(" + runInfo + ")"
  }
  private def copy(runInfo: Option[sbt.internal.worker.RunInfo]): ClientJobParams = {
    new ClientJobParams(runInfo)
  }
  def withRunInfo(runInfo: Option[sbt.internal.worker.RunInfo]): ClientJobParams = {
    copy(runInfo = runInfo)
  }
  def withRunInfo(runInfo: sbt.internal.worker.RunInfo): ClientJobParams = {
    copy(runInfo = Option(runInfo))
  }
}
object ClientJobParams {
  
  def apply(runInfo: Option[sbt.internal.worker.RunInfo]): ClientJobParams = new ClientJobParams(runInfo)
  def apply(runInfo: sbt.internal.worker.RunInfo): ClientJobParams = new ClientJobParams(Option(runInfo))
}
