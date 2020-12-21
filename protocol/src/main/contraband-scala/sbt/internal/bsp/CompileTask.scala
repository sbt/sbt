/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** Compile Notifications */
final class CompileTask private (
  val target: sbt.internal.bsp.BuildTargetIdentifier) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: CompileTask => (this.target == x.target)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.CompileTask".##) + target.##)
  }
  override def toString: String = {
    "CompileTask(" + target + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target): CompileTask = {
    new CompileTask(target)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): CompileTask = {
    copy(target = target)
  }
}
object CompileTask {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier): CompileTask = new CompileTask(target)
}
