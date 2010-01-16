import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val sub = project("sub", "Sub Project", new SubProject(_))
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")

	class SubProject(info: ProjectInfo) extends DefaultProject(info)
	{
	override def ivyCacheDirectory = Some(outputPath / "ivy-cache")
		override def ivyXML =
			<dependencies>
				<dependency org="com.camptocamp.tl.caltar" name="core" rev="0.5" transitive="false"/>
			</dependencies>
	}
}