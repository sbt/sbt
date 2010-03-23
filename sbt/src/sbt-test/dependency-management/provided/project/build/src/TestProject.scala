import sbt._

class TestProject(info: ProjectInfo) extends DefaultWebProject(info)
{
	override def useMavenConfigurations = true
	private val provided = "useProvided".asFile.exists
	private val configuration = if(provided) Configurations.Provided else Configurations.Compile
	val j = "javax.servlet" % "servlet-api" % "2.5" % (configuration.name + "->default")

	lazy val checkPublic = check(publicClasspath, !provided)
	lazy val checkRun = check(runClasspath, true)
	lazy val checkCompile = check(compileClasspath, true)
	lazy val checkProvided = check(fullClasspath(Configurations.Provided), provided)

	private def check(classpath: PathFinder, shouldBeIncluded: Boolean) =
		task { checkServletAPI(shouldBeIncluded, "classpath")(classpath.get) }
		
	lazy val checkWar = task { Control.thread(FileUtilities.unzip(warPath, outputPath / "exploded", log))(checkServletAPI(!provided, "war")) }
	private def checkServletAPI(shouldBeIncluded: Boolean, label: String)(paths: Iterable[Path]) =
	{
		val servletAPI = paths.find(_.asFile.getName.contains("servlet-api"))
		if(shouldBeIncluded)
		{
			if(servletAPI.isEmpty)
				Some("Servlet API should have been included in " + label + ".")
			else
				None
		}
		else
			servletAPI.map(_ + " incorrectly included in " + label + ".")
	}
}