/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param target The build target that contains the test classes.
 * @param classes The main class items
 */
final class ScalaMainClassesItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val classes: Vector[sbt.internal.bsp.ScalaMainClass]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ScalaMainClassesItem => (this.target == x.target) && (this.classes == x.classes)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaMainClassesItem".##) + target.##) + classes.##)
  }
  override def toString: String = {
    "ScalaMainClassesItem(" + target + ", " + classes + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, classes: Vector[sbt.internal.bsp.ScalaMainClass] = classes): ScalaMainClassesItem = {
    new ScalaMainClassesItem(target, classes)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): ScalaMainClassesItem = {
    copy(target = target)
  }
  def withClasses(classes: Vector[sbt.internal.bsp.ScalaMainClass]): ScalaMainClassesItem = {
    copy(classes = classes)
  }
}
object ScalaMainClassesItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, classes: Vector[sbt.internal.bsp.ScalaMainClass]): ScalaMainClassesItem = new ScalaMainClassesItem(target, classes)
}
