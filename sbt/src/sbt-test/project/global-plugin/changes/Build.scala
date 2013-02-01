import sbt._
import sbt.Keys._

object MyBuild extends Build {
  lazy val mySettings = Defaults.defaultSettings ++ Seq(
    name := "my-test-proj",
    organization := "com.example",
    check <<= update map checkVersion,
    version := "0.1.0-SNAPSHOT")

  lazy val proj = Project("my-test-proj", file("."), settings = mySettings)

  lazy val check = taskKey[Unit]("Verifies that the junit dependency has the older version (4.5)")

  def checkVersion(report: UpdateReport) {
    for(mod <- report.allModules) {
      if(mod.name == "junit") assert(mod.revision == "4.5", s"JUnit version (${mod.revision}) was not overridden")
    }
  }
}

