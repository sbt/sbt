import sbt._
import Keys._

object PomTest extends Build
{
	lazy val custom = config("custom")
	lazy val root = Project("root", file("root")) configs(custom) settings(
		TaskKey[Unit]("check-pom") <<= checkPom,
		libraryDependencies ++= Seq(
			"a" % "a" % "1.0",
			"b" % "b" % "1.0" % "runtime,optional",
			"c" % "c" % "1.0" % "optional",
			"d" % "d" % "1.0" % "test",
			"e" % "e" % "1.0" % "custom",		
			"f" % "f" % "1.0" % "custom,optional,runtime",
			"g" % "g" % "1.0" % "custom,runtime" classifier "foo"
		)
	)



	def checkPom = makePom map { pom =>
		val expected = Seq(
			("a", Some("compile"), false, None),
			("b", Some("runtime"), true, None),
			("c", None, true, None),
			("d", Some("test"), false, None),
			("e", Some("custom"), false, None),
			("f", Some("runtime"), true, None),
			("g", Some("runtime"), false, Some("foo"))
		)
		val loaded = xml.XML.loadFile(pom)
		val deps = loaded \\ "dependency"
		expected foreach { case (id, scope, opt, classifier) =>
			val dep = deps.find(d => (d \ "artifactId").text == id).getOrElse( error("Dependency '" + id + "' not written to pom:\n" + loaded))

			val actualOpt = java.lang.Boolean.parseBoolean( (dep \\ "optional").text )
			println("Actual: " + actualOpt + ", opt: " + opt)
			assert(opt == actualOpt, "Invalid 'optional' section '" + (dep \\ "optional") + "', expected optional=" + opt)

			val actualScope = (dep \\ "scope") match { case Seq() => None; case x => Some(x.text) }
			val acutalClassifier = (dep \\ "classifier") match { case Seq() => None; case x => Some(x.text) }
			assert(actualScope == scope, "Invalid 'scope' section '" + (dep \\ "scope") + "', expected scope=" + scope)
			assert(acutalClassifier == classifier, "Invalid 'classifier' section '" + (dep \\ "classifier") + "', expected classifier=" + classifier)
		}
	}
}
