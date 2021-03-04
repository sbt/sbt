/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param target The build target that was compiled
 * @param originId An optional request id to know the origin of this report
 * @param errors The total number of reported errors compiling this target.
 * @param warnings The total number of reported warnings compiling the target.
 * @param time The total number of milliseconds it took to compile the target.
 */
final class CompileReport private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val originId: Option[String],
  val errors: Int,
  val warnings: Int,
  val time: Option[Int]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileReport => (this.target == x.target) && (this.originId == x.originId) && (this.errors == x.errors) && (this.warnings == x.warnings) && (this.time == x.time)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.CompileReport".##) + target.##) + originId.##) + errors.##) + warnings.##) + time.##)
  }
  override def toString: String = {
    "CompileReport(" + target + ", " + originId + ", " + errors + ", " + warnings + ", " + time + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, originId: Option[String] = originId, errors: Int = errors, warnings: Int = warnings, time: Option[Int] = time): CompileReport = {
    new CompileReport(target, originId, errors, warnings, time)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): CompileReport = {
    copy(target = target)
  }
  def withOriginId(originId: Option[String]): CompileReport = {
    copy(originId = originId)
  }
  def withOriginId(originId: String): CompileReport = {
    copy(originId = Option(originId))
  }
  def withErrors(errors: Int): CompileReport = {
    copy(errors = errors)
  }
  def withWarnings(warnings: Int): CompileReport = {
    copy(warnings = warnings)
  }
  def withTime(time: Option[Int]): CompileReport = {
    copy(time = time)
  }
  def withTime(time: Int): CompileReport = {
    copy(time = Option(time))
  }
}
object CompileReport {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: Option[String], errors: Int, warnings: Int, time: Option[Int]): CompileReport = new CompileReport(target, originId, errors, warnings, time)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: String, errors: Int, warnings: Int, time: Int): CompileReport = new CompileReport(target, Option(originId), errors, warnings, Option(time))
}
