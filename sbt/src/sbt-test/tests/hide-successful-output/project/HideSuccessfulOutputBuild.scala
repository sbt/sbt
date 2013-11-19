import sbt._
import Keys._
import scala.xml.XML
import Tests._
import Defaults._
import org.backuity.matchete.AssertionMatchers

object HideSuccessfulOutputBuild extends Build with AssertionMatchers {

	val check = taskKey[Unit]("make sure successful test output isn't present in the dumped stdout")

	val main = project.in(file(".")).settings(

		libraryDependencies += "com.novocode" % "junit-interface" % "0.10" % "test",

		// hiding successful output only works in forked mode
		fork in Test := true,

		check := {
			val content = IO.read(file("stdout.dump")).trim
			val ansiFreeContent = content.replaceAll("\\p{C}", "")
			ansiFreeContent must not(contain("shouldn't show up"))
			ansiFreeContent must contain("must show up")
		}
	)
}