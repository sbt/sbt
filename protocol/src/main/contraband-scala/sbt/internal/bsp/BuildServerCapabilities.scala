/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param compileProvider The languages the server supports compilation via method buildTarget/compile. */
final class BuildServerCapabilities private (
  val compileProvider: Option[sbt.internal.bsp.CompileProvider]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: BuildServerCapabilities => (this.compileProvider == x.compileProvider)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.BuildServerCapabilities".##) + compileProvider.##)
  }
  override def toString: String = {
    "BuildServerCapabilities(" + compileProvider + ")"
  }
  private[this] def copy(compileProvider: Option[sbt.internal.bsp.CompileProvider] = compileProvider): BuildServerCapabilities = {
    new BuildServerCapabilities(compileProvider)
  }
  def withCompileProvider(compileProvider: Option[sbt.internal.bsp.CompileProvider]): BuildServerCapabilities = {
    copy(compileProvider = compileProvider)
  }
  def withCompileProvider(compileProvider: sbt.internal.bsp.CompileProvider): BuildServerCapabilities = {
    copy(compileProvider = Option(compileProvider))
  }
}
object BuildServerCapabilities {
  
  def apply(compileProvider: Option[sbt.internal.bsp.CompileProvider]): BuildServerCapabilities = new BuildServerCapabilities(compileProvider)
  def apply(compileProvider: sbt.internal.bsp.CompileProvider): BuildServerCapabilities = new BuildServerCapabilities(Option(compileProvider))
}
