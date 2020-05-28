/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/** @param sources List of resources containing source files of the target's dependencies.
Can be source files, jar files, zip files, or directories */
final class DependencySourcesItem private (
  val target: Option[sbt.internal.bsp.BuildTargetIdentifier],
  val sources: Vector[java.net.URI]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: DependencySourcesItem => (this.target == x.target) && (this.sources == x.sources)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.DependencySourcesItem".##) + target.##) + sources.##)
  }
  override def toString: String = {
    "DependencySourcesItem(" + target + ", " + sources + ")"
  }
  private[this] def copy(target: Option[sbt.internal.bsp.BuildTargetIdentifier] = target, sources: Vector[java.net.URI] = sources): DependencySourcesItem = {
    new DependencySourcesItem(target, sources)
  }
  def withTarget(target: Option[sbt.internal.bsp.BuildTargetIdentifier]): DependencySourcesItem = {
    copy(target = target)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): DependencySourcesItem = {
    copy(target = Option(target))
  }
  def withSources(sources: Vector[java.net.URI]): DependencySourcesItem = {
    copy(sources = sources)
  }
}
object DependencySourcesItem {
  
  def apply(target: Option[sbt.internal.bsp.BuildTargetIdentifier], sources: Vector[java.net.URI]): DependencySourcesItem = new DependencySourcesItem(target, sources)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, sources: Vector[java.net.URI]): DependencySourcesItem = new DependencySourcesItem(Option(target), sources)
}
