import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
{
	val httpclient = "org.apache.httpcomponents" % "httpclient" % "4.0-beta2" intransitive()
	
	override def useDefaultConfigurations =
		if("useDefaultConfigurations".asFile.exists) true
		else false

	lazy val checkDefault = task { check(Configurations.Default) }
	lazy val checkCompile = task { check(Configurations.Compile) }
	lazy val checkClasspath = task { if(compileClasspath.get.isEmpty) Some("Dependency in default configuration not added to classpath") else None }

	private def check(config: Configuration) =
		if(configurationClasspath(config).get.isEmpty)
			Some("Dependency in " + config.name + " configuration not downloaded")
		else
			None
}