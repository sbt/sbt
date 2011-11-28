import sbt._
import Keys._

object SbtExtras extends Build {
  // TODO - Detect Debian distribution and enable debian settings for the project.
  val root = Project("sbt-extras", file(".")) settings(DebianPkg.settings:_*)
}
