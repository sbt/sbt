import sbt._
import java.io.File
import java.lang.System

class JettyEnvTestProject(info: ProjectInfo) extends DefaultWebProject(info){
	override def jettyEnvXml = Some( (info.projectPath / "conf" / "jetty" / "jetty-env.xml").asFile)
	val jetty7WebApp = "org.eclipse.jetty" % "jetty-webapp" % "7.0.2.RC0" % "test"
	val jetty7Plus = "org.eclipse.jetty" % "jetty-plus" % "7.0.2.RC0" % "test"
	val servletApiDep = "javax.servlet" % "servlet-api" % "2.5" % "provided"
}
