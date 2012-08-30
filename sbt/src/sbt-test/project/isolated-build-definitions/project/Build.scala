import sbt._
import Keys._

object B extends Build
{
  lazy val projectRuntime = Project(id = "project-runtime", base = file(".")).
    dependsOn(projectGenerator % "optional")


  lazy val projectGenerator = ProjectRef(uri("generator/"),"project")
}
