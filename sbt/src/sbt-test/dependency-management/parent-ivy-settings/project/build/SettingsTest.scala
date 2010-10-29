import sbt._

class SettingsTest(info: ProjectInfo) extends DefaultProject(info)
{
	val child = project("sub", "Sub", new SubProject(_))

	class SubProject(info: ProjectInfo) extends DefaultProject(info)
	{
		val dispatch = "net.databinder" % "databinder-dispatch" % "1.1.2" intransitive()
	}
}