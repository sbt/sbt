/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param target The build target that contains the test classes.
 * @param classes The fully qualified names of the test classes in this target
 * @param framework test framework name
 */
final class ScalaTestClassesItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val classes: Vector[String],
  val framework: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaTestClassesItem => (this.target == x.target) && (this.classes == x.classes) && (this.framework == x.framework)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.ScalaTestClassesItem".##) + target.##) + classes.##) + framework.##)
  }
  override def toString: String = {
    "ScalaTestClassesItem(" + target + ", " + classes + ", " + framework + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, classes: Vector[String] = classes, framework: Option[String] = framework): ScalaTestClassesItem = {
    new ScalaTestClassesItem(target, classes, framework)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): ScalaTestClassesItem = {
    copy(target = target)
  }
  def withClasses(classes: Vector[String]): ScalaTestClassesItem = {
    copy(classes = classes)
  }
  def withFramework(framework: Option[String]): ScalaTestClassesItem = {
    copy(framework = framework)
  }
  def withFramework(framework: String): ScalaTestClassesItem = {
    copy(framework = Option(framework))
  }
}
object ScalaTestClassesItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, classes: Vector[String], framework: Option[String]): ScalaTestClassesItem = new ScalaTestClassesItem(target, classes, framework)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, classes: Vector[String], framework: String): ScalaTestClassesItem = new ScalaTestClassesItem(target, classes, Option(framework))
}
