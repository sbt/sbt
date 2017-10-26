
import sbt._
import sbt.Defaults.itSettings
import sbt.Keys._

object Aliases {

  def libs = libraryDependencies

  def hasITs = itSettings

  def ShadingPlugin = coursier.ShadingPlugin

  def root = file(".")


  implicit class ProjectOps(val proj: Project) extends AnyVal {
    def dummy: Project =
      proj.in(file(s"target/${proj.id}"))
  }

}
