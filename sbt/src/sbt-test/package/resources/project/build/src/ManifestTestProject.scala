import sbt._

class ManifestTestProject(info: ProjectInfo) extends DefaultProject(info)
{
	override def mainClass = Some("jartest.Main")
	def manifestExtra =
	{
		import java.util.jar._
		val mf = new Manifest
		mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, buildScalaInstance.libraryJar.getAbsolutePath)
		mf
	}
	override def packageOptions = JarManifest(manifestExtra) :: super.packageOptions.toList
	override def disableCrossPaths = true
}