import sbt._
import Keys._
import complete._
import complete.DefaultParsers._

object MyBuild extends Build
{
	lazy val root = Project("root", file(".")) settings( externalPom() :_*) settings(
		scalaVersion := "2.9.0-1",
		check <<= checkTask,
		managedClasspath in Provided <<= (classpathTypes, update) map { (cpts, report) => Classpaths.managedJars(Provided, cpts, report) }
	)

	def checkTask = InputTask(_ => parser ) { result =>
			(result, managedClasspath in Provided, fullClasspath in Compile, fullClasspath in Test, fullClasspath in Runtime) map { case ((conf, names), p, c, t, r) =>
				println("Checking: " + conf.name)
				checkClasspath(conf match {
					case Provided => p
					case Compile => c
					case Test => t 
					case Runtime => r
				}, names.toSet)
			}
		}

	lazy val check = InputKey[Unit]("check")
	def parser: Parser[(Configuration,Seq[String])] = (Space ~> token(cp(Compile) | cp(Runtime) | cp(Provided) | cp(Test))) ~ spaceDelimited("<module-names>")
	def cp(c: Configuration): Parser[Configuration] = c.name ^^^ c
	def checkClasspath(cp: Seq[Attributed[File]], names: Set[String]) =
	{
		val fs = cp.files filter { _.getName endsWith ".jar" }
		val intersect = fs filter { f => names exists { f.getName startsWith _ } }
		assert(intersect == fs, "Expected:" + seqStr(names.toSeq) + "Got: " + seqStr(fs))
		()
	}
	def seqStr(s: Seq[_]) = s.mkString("\n\t", "\n\t", "\n")
}