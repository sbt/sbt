/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Javac options
 * The build target javac options request is sent from the client to the server
 * to query for the list of compiler options necessary to compile in a given list of targets.
 */
final class JavacOptionsParams private (
  val targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JavacOptionsParams => (this.targets == x.targets)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (17 + "sbt.internal.bsp.JavacOptionsParams".##) + targets.##)
  }
  override def toString: String = {
    "JavacOptionsParams(" + targets + ")"
  }
  private[this] def copy(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier] = targets): JavacOptionsParams = {
    new JavacOptionsParams(targets)
  }
  def withTargets(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): JavacOptionsParams = {
    copy(targets = targets)
  }
}
object JavacOptionsParams {
  
  def apply(targets: Vector[sbt.internal.bsp.BuildTargetIdentifier]): JavacOptionsParams = new JavacOptionsParams(targets)
}
