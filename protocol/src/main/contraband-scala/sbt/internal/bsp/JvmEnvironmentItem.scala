/**
 * This code is generated using [[https://www.scala-sbt.org/contraband/ sbt-contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.bsp
final class JvmEnvironmentItem private (
  val target: sbt.internal.bsp.BuildTargetIdentifier,
  val classpath: Vector[java.net.URI],
  val jvmOptions: Vector[String],
  val workingDirectory: String,
  val environmentVariables: scala.collection.immutable.Map[String, String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: JvmEnvironmentItem => (this.target == x.target) && (this.classpath == x.classpath) && (this.jvmOptions == x.jvmOptions) && (this.workingDirectory == x.workingDirectory) && (this.environmentVariables == x.environmentVariables)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.bsp.JvmEnvironmentItem".##) + target.##) + classpath.##) + jvmOptions.##) + workingDirectory.##) + environmentVariables.##)
  }
  override def toString: String = {
    "JvmEnvironmentItem(" + target + ", " + classpath + ", " + jvmOptions + ", " + workingDirectory + ", " + environmentVariables + ")"
  }
  private[this] def copy(target: sbt.internal.bsp.BuildTargetIdentifier = target, classpath: Vector[java.net.URI] = classpath, jvmOptions: Vector[String] = jvmOptions, workingDirectory: String = workingDirectory, environmentVariables: scala.collection.immutable.Map[String, String] = environmentVariables): JvmEnvironmentItem = {
    new JvmEnvironmentItem(target, classpath, jvmOptions, workingDirectory, environmentVariables)
  }
  def withTarget(target: sbt.internal.bsp.BuildTargetIdentifier): JvmEnvironmentItem = {
    copy(target = target)
  }
  def withClasspath(classpath: Vector[java.net.URI]): JvmEnvironmentItem = {
    copy(classpath = classpath)
  }
  def withJvmOptions(jvmOptions: Vector[String]): JvmEnvironmentItem = {
    copy(jvmOptions = jvmOptions)
  }
  def withWorkingDirectory(workingDirectory: String): JvmEnvironmentItem = {
    copy(workingDirectory = workingDirectory)
  }
  def withEnvironmentVariables(environmentVariables: scala.collection.immutable.Map[String, String]): JvmEnvironmentItem = {
    copy(environmentVariables = environmentVariables)
  }
}
object JvmEnvironmentItem {
  
  def apply(target: sbt.internal.bsp.BuildTargetIdentifier, classpath: Vector[java.net.URI], jvmOptions: Vector[String], workingDirectory: String, environmentVariables: scala.collection.immutable.Map[String, String]): JvmEnvironmentItem = new JvmEnvironmentItem(target, classpath, jvmOptions, workingDirectory, environmentVariables)
}
