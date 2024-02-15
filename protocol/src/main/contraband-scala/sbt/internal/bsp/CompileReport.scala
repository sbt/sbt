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
 * @param noOp The compilation was a noOp compilation.
 */
final class CompileReport private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val originId: Option[String],
  val errors: Int,
  val warnings: Int,
  val time: Option[Int],
  val noOp: Option[Boolean]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileReport => (this.target == x.target) && (this.originId == x.originId) && (this.errors == x.errors) && (this.warnings == x.warnings) && (this.time == x.time) && (this.noOp == x.noOp)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.CompileReport".##) + target.##) + originId.##) + errors.##) + warnings.##) + time.##) + noOp.##)
  }
  override def toString: String = {
    "CompileReport(" + target + ", " + originId + ", " + errors + ", " + warnings + ", " + time + ", " + noOp + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, originId: Option[String] = originId, errors: Int = errors, warnings: Int = warnings, time: Option[Int] = time, noOp: Option[Boolean] = noOp): CompileReport = {
    new CompileReport(target, originId, errors, warnings, time, noOp)
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
  def withNoOp(noOp: Option[Boolean]): CompileReport = {
    copy(noOp = noOp)
  }
  def withNoOp(noOp: Boolean): CompileReport = {
    copy(noOp = Option(noOp))
  }
}
object CompileReport {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: Option[String], errors: Int, warnings: Int, time: Option[Int], noOp: Option[Boolean]): CompileReport = new CompileReport(target, originId, errors, warnings, time, noOp)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, originId: String, errors: Int, warnings: Int, time: Int, noOp: Boolean): CompileReport = new CompileReport(target, Option(originId), errors, warnings, Option(time), Option(noOp))
}
