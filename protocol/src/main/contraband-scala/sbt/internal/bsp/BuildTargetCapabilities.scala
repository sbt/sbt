/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param canCompile This target can be compiled by the BSP server.
 * @param canTest This target can be tested by the BSP server.
 * @param canRun This target can be run by the BSP server.
 * @param canDebug This target can be debugged by the BSP server.
 */
final class BuildTargetCapabilities private (
  val canCompile: Boolean,
  val canTest: Boolean,
  val canRun: Boolean,
  val canDebug: Boolean) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: BuildTargetCapabilities => (this.canCompile == x.canCompile) && (this.canTest == x.canTest) && (this.canRun == x.canRun) && (this.canDebug == x.canDebug)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.BuildTargetCapabilities".##) + canCompile.##) + canTest.##) + canRun.##) + canDebug.##)
  }
  override def toString: String = {
    "BuildTargetCapabilities(" + canCompile + ", " + canTest + ", " + canRun + ", " + canDebug + ")"
  }
  private[this] def copy(canCompile: Boolean = canCompile, canTest: Boolean = canTest, canRun: Boolean = canRun, canDebug: Boolean = canDebug): BuildTargetCapabilities = {
    new BuildTargetCapabilities(canCompile, canTest, canRun, canDebug)
  }
  def withCanCompile(canCompile: Boolean): BuildTargetCapabilities = {
    copy(canCompile = canCompile)
  }
  def withCanTest(canTest: Boolean): BuildTargetCapabilities = {
    copy(canTest = canTest)
  }
  def withCanRun(canRun: Boolean): BuildTargetCapabilities = {
    copy(canRun = canRun)
  }
  def withCanDebug(canDebug: Boolean): BuildTargetCapabilities = {
    copy(canDebug = canDebug)
  }
}
object BuildTargetCapabilities {
  
  def apply(canCompile: Boolean, canTest: Boolean, canRun: Boolean, canDebug: Boolean): BuildTargetCapabilities = new BuildTargetCapabilities(canCompile, canTest, canRun, canDebug)
}
