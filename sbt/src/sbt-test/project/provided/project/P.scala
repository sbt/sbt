import sbt._
import Keys._

object P extends Build
{
	lazy val superRoot = Project("super-root", file("super")) dependsOn(root)
	lazy val root: Project = Project("root", file("."), settings = rootSettings, dependencies = (sub % "provided->test") :: Nil)
	lazy val sub = Project("sub", file("sub"))

	def rootSettings = Defaults.defaultSettings :+ ( TaskKey[Unit]("check") <<= checkTask )
	def checkTask = (fullClasspath in (root, Compile), fullClasspath in (root, Runtime), fullClasspath in (root, Test), fullClasspath in (sub, Test), fullClasspath in (superRoot, Compile)) map { (rc, rr, rt, st, pr) =>
		check0(st, "sub test", true)
		check0(pr, "super main", false)
		check0(rc, "root main", true)
		check0(rr, "root runtime", false)
		check0(rt, "root test", true)
	}
	def check0(cp: Seq[Attributed[File]], label: String, shouldSucceed: Boolean): Unit =
	{
		val loader = classpath.ClasspathUtilities.toLoader(cp.files)
		println("Checking " + label)
		val err = try { Class.forName("org.example.ProvidedTest", false, loader); None }
		catch { case e: Exception => Some(e) }

		(err, shouldSucceed) match
		{
			case (None, true) | (Some(_), false) => ()
			case (None, false) => error("Expected failure")
			case (Some(x), true) => throw x
		}
	}
}