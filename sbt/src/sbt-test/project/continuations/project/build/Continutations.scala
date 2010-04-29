import sbt._

class Continuations(info: ProjectInfo) extends DefaultProject(info) with AutoCompilerPlugins
{
	val cont = compilerPlugin("org.scala-lang.plugins" % "continuations" % "2.8.0.RC1")
	override def compileOptions = super.compileOptions ++ compileOptions("-P:continuations:enable")

	val junit = "junit" % "junit" % "4.7" % "test"
	val bryanjswift = "Bryan J Swift Repository" at "http://repos.bryanjswift.com/maven2/"
	val junitInterface = "com.novocode" % "junit-interface" % "0.4.0" % "test"

	override def consoleInit = """assert(Examples.x == 20)"""
}