/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param target The build target that contains the test classes.
 * @param classes The fully qualified names of the test classes in this target
 */
final class ScalaTestClassesItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val classes: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = o match {
    case x: ScalaTestClassesItem => (this.target == x.target) && (this.classes == x.classes)
    case _ => false
  }
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaTestClassesItem".##) + target.##) + classes.##)
  }
  override def toString: String = {
    "ScalaTestClassesItem(" + target + ", " + classes + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, classes: Vector[String] = classes): ScalaTestClassesItem = {
    new ScalaTestClassesItem(target, classes)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): ScalaTestClassesItem = {
    copy(target = target)
  }
  def withClasses(classes: Vector[String]): ScalaTestClassesItem = {
    copy(classes = classes)
  }
}
object ScalaTestClassesItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, classes: Vector[String]): ScalaTestClassesItem = new ScalaTestClassesItem(target, classes)
}
