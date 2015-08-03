import sbt._
import sbt.Keys._

object MyBuild extends Build {
  lazy val mySettings = Defaults.defaultSettings ++ Seq(
    name := "my-test-proj",
    organization := "com.example",
    check <<= update map checkVersion,
    version := "0.1.0-SNAPSHOT")

  lazy val proj = Project("my-test-proj", file("."), settings = mySettings)

  lazy val check = taskKey[Unit]("Verifies that the junit dependency has the newer version (4.8)")

  def checkVersion(report: UpdateReport): Unit = {
    for(mod <- report.allModules) {
      if(mod.name == "junit") assert(mod.revision == "4.8", s"JUnit version (${mod.revision}) does not have the correct version")
    }
  }
}
