import sbt._
import Keys._
import Import._

object B extends Build {
lazy val project = Project(id = "project",
                            base = file("."))
}
