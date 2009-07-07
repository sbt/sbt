import sbt._

class ManifestTestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val scalaHome = system[String]("scala.home")
	override def mainClass = Some("jartest.Main")
	def manifestExtra =
	{
		import java.util.jar._
		val mf = new Manifest
		for(scalaH <- scalaHome.get)
			mf.getMainAttributes.put(Attributes.Name.CLASS_PATH, scalaH + "/lib/scala-library.jar")
		mf
	}
	override def packageOptions = JarManifest(manifestExtra) :: super.packageOptions.toList
}