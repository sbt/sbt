/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
/**
 * Contains jvm-specific metadata, specifically JDK reference
 * @param javaHome Uri representing absolute path to jdk
 * @param javaVersion The java version this target is supposed to use (can be set using javac `-target` flag)
 */
final class JvmBuildTarget private (
  val javaHome: Option[java.net.URI],
  val javaVersion: Option[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmBuildTarget => (this.javaHome == x.javaHome) && (this.javaVersion == x.javaVersion)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmBuildTarget".##) + javaHome.##) + javaVersion.##)
  }
  override def toString: String = {
    "JvmBuildTarget(" + javaHome + ", " + javaVersion + ")"
  }
  private def copy(javaHome: Option[java.net.URI] = javaHome, javaVersion: Option[String] = javaVersion): JvmBuildTarget = {
    new JvmBuildTarget(javaHome, javaVersion)
  }
  def withJavaHome(javaHome: Option[java.net.URI]): JvmBuildTarget = {
    copy(javaHome = javaHome)
  }
  def withJavaHome(javaHome: java.net.URI): JvmBuildTarget = {
    copy(javaHome = Option(javaHome))
  }
  def withJavaVersion(javaVersion: Option[String]): JvmBuildTarget = {
    copy(javaVersion = javaVersion)
  }
  def withJavaVersion(javaVersion: String): JvmBuildTarget = {
    copy(javaVersion = Option(javaVersion))
  }
}
object JvmBuildTarget {
  
  def apply(javaHome: Option[java.net.URI], javaVersion: Option[String]): JvmBuildTarget = new JvmBuildTarget(javaHome, javaVersion)
  def apply(javaHome: java.net.URI, javaVersion: String): JvmBuildTarget = new JvmBuildTarget(Option(javaHome), Option(javaVersion))
}
