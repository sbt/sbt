import sbt._

class TestProject(info: ProjectInfo) extends ParentProject(info)
{
	val sub = project("sub", "Sub Project", new SubProject(_))
	def ivyCacheDirectory = outputPath / "ivy-cache"
	override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList

	class SubProject(info: ProjectInfo) extends DefaultProject(info)
	{
		def ivyCacheDirectory = outputPath / "ivy-cache"
		override def updateOptions =  CacheDirectory(ivyCacheDirectory) :: super.updateOptions.toList
		override def ivyXML =
			<dependencies>
				<dependency org="com.camptocamp.tl.caltar" name="core" rev="0.5" transitive="false"/>
			</dependencies>
	}
}