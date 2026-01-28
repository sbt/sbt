/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
/** Configuration for creating a ScalaInstance in forked process. */
final class ScalaInstanceConfig private (
  val scalaVersion: String,
  val libraryJars: Vector[java.net.URI],
  val allCompilerJars: Vector[java.net.URI],
  val extraToolJars: Vector[java.net.URI]) extends Serializable {
  
  private def this(scalaVersion: String, libraryJars: Vector[java.net.URI], allCompilerJars: Vector[java.net.URI]) = this(scalaVersion, libraryJars, allCompilerJars, Vector())
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaInstanceConfig => (this.scalaVersion == x.scalaVersion) && (this.libraryJars == x.libraryJars) && (this.allCompilerJars == x.allCompilerJars) && (this.extraToolJars == x.extraToolJars)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.ScalaInstanceConfig".##) + scalaVersion.##) + libraryJars.##) + allCompilerJars.##) + extraToolJars.##)
  }
  override def toString: String = {
    "ScalaInstanceConfig(" + scalaVersion + ", " + libraryJars + ", " + allCompilerJars + ", " + extraToolJars + ")"
  }
  private def copy(scalaVersion: String = scalaVersion, libraryJars: Vector[java.net.URI] = libraryJars, allCompilerJars: Vector[java.net.URI] = allCompilerJars, extraToolJars: Vector[java.net.URI] = extraToolJars): ScalaInstanceConfig = {
    new ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars, extraToolJars)
  }
  def withScalaVersion(scalaVersion: String): ScalaInstanceConfig = {
    copy(scalaVersion = scalaVersion)
  }
  def withLibraryJars(libraryJars: Vector[java.net.URI]): ScalaInstanceConfig = {
    copy(libraryJars = libraryJars)
  }
  def withAllCompilerJars(allCompilerJars: Vector[java.net.URI]): ScalaInstanceConfig = {
    copy(allCompilerJars = allCompilerJars)
  }
  def withExtraToolJars(extraToolJars: Vector[java.net.URI]): ScalaInstanceConfig = {
    copy(extraToolJars = extraToolJars)
  }
}
object ScalaInstanceConfig {
  
  def apply(scalaVersion: String, libraryJars: Vector[java.net.URI], allCompilerJars: Vector[java.net.URI]): ScalaInstanceConfig = new ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars)
  def apply(scalaVersion: String, libraryJars: Vector[java.net.URI], allCompilerJars: Vector[java.net.URI], extraToolJars: Vector[java.net.URI]): ScalaInstanceConfig = new ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars, extraToolJars)
}
