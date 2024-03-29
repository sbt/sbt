/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * @param options Additional arguments to the compiler.
                  For example, -deprecation.
 * @param classpath The dependency classpath for this target, must be
                    identical to what is passed as arguments to
                    the -classpath flag in the command line interface
                    of scalac.
 * @param classDirectory The output directory for classfiles produced by this target
 */
final class JavacOptionsItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val options: Vector[String],
  val classpath: Vector[java.net.URI],
  val classDirectory: Option[java.net.URI]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JavacOptionsItem => (this.target == x.target) && (this.options == x.options) && (this.classpath == x.classpath) && (this.classDirectory == x.classDirectory)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.JavacOptionsItem".##) + target.##) + options.##) + classpath.##) + classDirectory.##)
  }
  override def toString: String = {
    "JavacOptionsItem(" + target + ", " + options + ", " + classpath + ", " + classDirectory + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, options: Vector[String] = options, classpath: Vector[java.net.URI] = classpath, classDirectory: Option[java.net.URI] = classDirectory): JavacOptionsItem = {
    new JavacOptionsItem(target, options, classpath, classDirectory)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): JavacOptionsItem = {
    copy(target = target)
  }
  def withOptions(options: Vector[String]): JavacOptionsItem = {
    copy(options = options)
  }
  def withClasspath(classpath: Vector[java.net.URI]): JavacOptionsItem = {
    copy(classpath = classpath)
  }
  def withClassDirectory(classDirectory: Option[java.net.URI]): JavacOptionsItem = {
    copy(classDirectory = classDirectory)
  }
  def withClassDirectory(classDirectory: java.net.URI): JavacOptionsItem = {
    copy(classDirectory = Option(classDirectory))
  }
}
object JavacOptionsItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, options: Vector[String], classpath: Vector[java.net.URI], classDirectory: Option[java.net.URI]): JavacOptionsItem = new JavacOptionsItem(target, options, classpath, classDirectory)
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, options: Vector[String], classpath: Vector[java.net.URI], classDirectory: java.net.URI): JavacOptionsItem = new JavacOptionsItem(target, options, classpath, Option(classDirectory))
}
