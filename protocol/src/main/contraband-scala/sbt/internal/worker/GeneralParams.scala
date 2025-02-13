/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
/**
 * Parameter for the sbt/general command, which is a generic command to
 * run a program.
 */
final class GeneralParams private (
  val runInfo: Option[sbt.internal.worker.RunInfo]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: GeneralParams => (this.runInfo == x.runInfo)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.worker.GeneralParams".##) + runInfo.##)
  }
  override def toString: String = {
    "GeneralParams(" + runInfo + ")"
  }
  private def copy(runInfo: Option[sbt.internal.worker.RunInfo] = runInfo): GeneralParams = {
    new GeneralParams(runInfo)
  }
  def withRunInfo(runInfo: Option[sbt.internal.worker.RunInfo]): GeneralParams = {
    copy(runInfo = runInfo)
  }
  def withRunInfo(runInfo: sbt.internal.worker.RunInfo): GeneralParams = {
    copy(runInfo = Option(runInfo))
  }
}
object GeneralParams {
  
  def apply(runInfo: Option[sbt.internal.worker.RunInfo]): GeneralParams = new GeneralParams(runInfo)
  def apply(runInfo: sbt.internal.worker.RunInfo): GeneralParams = new GeneralParams(Option(runInfo))
}
