import sbt._
import java.io.File
import java.lang.System
import Process._

class JettyEnvTestProject(info: ProjectInfo) extends DefaultWebProject(info){
	override def jettyEnvXml = Some( (info.projectPath / "conf" / "jetty" / "jetty-env.xml").asFile)
	val jetty7WebApp = "org.eclipse.jetty" % "jetty-webapp" % "7.0.2.RC0" % "test"
	val jetty7Plus = "org.eclipse.jetty" % "jetty-plus" % "7.0.2.RC0" % "test"
	val servletApiDep = "javax.servlet" % "servlet-api" % "2.5" % "provided"

	def indexURL = new java.net.URL("http://localhost:" + jettyPort)
	def indexFile = new java.io.File("index.html")
	override def jettyPort = 7127
	lazy val getPage = execTask { indexURL #> indexFile }
	
	lazy val checkPage = task { args => task { checkHelloWorld(args.mkString(" ")) } dependsOn getPage }
	
	private def checkHelloWorld(checkString: String) =
	{
		val value = xsbt.FileUtilities.read(indexFile)
		if(value.contains(checkString)) None else Some("index.html did not contain '" + checkString + "' :\n" +value)
	}
}
