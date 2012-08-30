import sbt._
import Keys._

object B extends Build {
lazy val project = Project(id = "project",
                            base = file("."))
}
