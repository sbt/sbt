import sbt._

class SettingsTest(info: ProjectInfo) extends DefaultProject(info)
{
	val child = project("sub", "Sub", new SubProject(_))

	class SubProject(info: ProjectInfo) extends DefaultProject(info)
	{
		val configgy = "net.lag" % "configgy" % "1.1"
	}
}