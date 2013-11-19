import sbt._
import Keys._
import Tests._
import Defaults._
import org.backuity.matchete.{FileMatchers,AssertionMatchers}

object ForkMultiVmTest extends Build with AssertionMatchers with FileMatchers {

	val check = taskKey[Unit]("Check that tests are executed in multiple vms")
	val checkClean = taskKey[Unit]("Check that clean left no test results")

	lazy val root = project.in(file(".")).settings(
		scalaVersion := "2.9.2",
		libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",
		checkClean := {
			for(i <- 1 to 4) file("target/" + i) must not(exist)
		},
		check := {
			file("target/1") must exist
			for(i <- 4 to 2 by -1) {
				file("target/" + i) must not(exist)
			}
		}
	)
}