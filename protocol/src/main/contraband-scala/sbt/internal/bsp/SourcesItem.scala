/**
 * This code is generated using [[http://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param sources The text documents or and directories that belong to this build target. */
final class SourcesItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val sources: Vector[sbt.internal.bsp.SourceItem]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: SourcesItem => (this.target == x.target) && (this.sources == x.sources)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.SourcesItem".##) + target.##) + sources.##)
  }
  override def toString: String = {
    "SourcesItem(" + target + ", " + sources + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, sources: Vector[sbt.internal.bsp.SourceItem] = sources): SourcesItem = {
    new SourcesItem(target, sources)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): SourcesItem = {
    copy(target = target)
  }
  def withSources(sources: Vector[sbt.internal.bsp.SourceItem]): SourcesItem = {
    copy(sources = sources)
  }
}
object SourcesItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, sources: Vector[sbt.internal.bsp.SourceItem]): SourcesItem = new SourcesItem(target, sources)
}
