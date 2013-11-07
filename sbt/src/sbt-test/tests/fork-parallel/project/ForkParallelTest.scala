import sbt._
import Keys._
import Tests._
import Defaults._

object ForkParallelTest extends Build {
	val check = taskKey[Unit]("Check that tests are executed in parallel")

	lazy val root = Project("root", file("."), settings = defaultSettings ++ Seq(
		scalaVersion := "2.9.2",
		libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",
		fork in Test := true,
		check := {
			if( ! (file("max-concurrent-tests_3").exists() || file("max-concurrent-tests_4").exists() )) {
				sys.error("Forked tests were not executed in parallel!")
			}
		}
	))
}