import sbt._
import Keys._

object SomeBuild extends Build {
  val buildSettings = Seq(
    organization := "com.softwaremill",
    version := "0.0.1-SNAPSHOT",
    scalaVersion := "2.10.0",
    libraryDependencies += "org.scalatest" %% "scalatest" % "1.9.1" % "test"
  )

  lazy val parent: Project = Project("root", file("."), aggregate = Seq(sub1,sub2)).settings(buildSettings : _*)

  lazy val sub1: Project = Project("sub1", file("sub1")).settings(buildSettings : _*).dependsOn(parent)
  lazy val sub2: Project = Project("sub2", file("sub2")).settings(buildSettings : _*).dependsOn(parent)
}
