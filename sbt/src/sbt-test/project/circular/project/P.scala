import sbt._
import Keys._

object Build extends Build {

  lazy val root: Project = Project(
    "root",
    file("."),
    aggregate = Seq(sub)
  )

  lazy val sub: Project = Project(
    "sub",
    file("sub"),
    dependencies = Seq(root)
  )
}
