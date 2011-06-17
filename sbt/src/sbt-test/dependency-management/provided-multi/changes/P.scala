import sbt._
import Keys._
object P extends Build
{
	val declared = SettingKey[Boolean]("declared")
	lazy val a = Project("A", file("a")) settings(
		libraryDependencies += "org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided"
	)

	lazy val b = Project("B", file("b")) dependsOn(a) settings(
		libraryDependencies <<= declared(d => if(d) Seq("org.scala-tools.sbinary" %% "sbinary" % "0.4.0" % "provided") else Nil),
		declared <<= baseDirectory(_ / "declare.lib" exists)
	)
}