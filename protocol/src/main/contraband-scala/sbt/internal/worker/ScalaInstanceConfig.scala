/**
 * This code is generated using [[https://www.scala-sbt.org/contraband]].
 */

// DO NOT EDIT MANUALLY
package sbt.internal.worker
/** Configuration for creating a ScalaInstance in forked process. */
final class ScalaInstanceConfig private (
  val scalaVersion: String,
  val libraryJars: Vector[String],
  val allCompilerJars: Vector[String],
  val allDocJars: Vector[String]) extends Serializable {
  
  
  
  override def equals(o: Any): Boolean = this.eq(o.asInstanceOf[AnyRef]) || (o match {
    case x: ScalaInstanceConfig => (this.scalaVersion == x.scalaVersion) && (this.libraryJars == x.libraryJars) && (this.allCompilerJars == x.allCompilerJars) && (this.allDocJars == x.allDocJars)
    case _ => false
  })
  override def hashCode: Int = {
    37 * (37 * (37 * (37 * (37 * (17 + "sbt.internal.worker.ScalaInstanceConfig".##) + scalaVersion.##) + libraryJars.##) + allCompilerJars.##) + allDocJars.##)
  }
  override def toString: String = {
    "ScalaInstanceConfig(" + scalaVersion + ", " + libraryJars + ", " + allCompilerJars + ", " + allDocJars + ")"
  }
  private def copy(scalaVersion: String = scalaVersion, libraryJars: Vector[String] = libraryJars, allCompilerJars: Vector[String] = allCompilerJars, allDocJars: Vector[String] = allDocJars): ScalaInstanceConfig = {
    new ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars, allDocJars)
  }
  def withScalaVersion(scalaVersion: String): ScalaInstanceConfig = {
    copy(scalaVersion = scalaVersion)
  }
  def withLibraryJars(libraryJars: Vector[String]): ScalaInstanceConfig = {
    copy(libraryJars = libraryJars)
  }
  def withAllCompilerJars(allCompilerJars: Vector[String]): ScalaInstanceConfig = {
    copy(allCompilerJars = allCompilerJars)
  }
  def withAllDocJars(allDocJars: Vector[String]): ScalaInstanceConfig = {
    copy(allDocJars = allDocJars)
  }
}
object ScalaInstanceConfig {
  
  def apply(scalaVersion: String, libraryJars: Vector[String], allCompilerJars: Vector[String], allDocJars: Vector[String]): ScalaInstanceConfig = new ScalaInstanceConfig(scalaVersion, libraryJars, allCompilerJars, allDocJars)
}
