import sbt._

class P(info: ProjectInfo) extends DefaultProject(info)
{
	val bryanjswift = "Bryan J Swift Repository" at "http://repos.bryanjswift.com/maven2/"
	val junitInterface = "com.novocode" % "junit-interface" % "0.4.0" % "test"
	val junit = "junit" % "junit" % "4.7" % "test"
}