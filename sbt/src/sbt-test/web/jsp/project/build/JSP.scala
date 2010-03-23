import sbt._

class JSP(info: ProjectInfo) extends DefaultWebProject(info)
{
	val j6 = "org.mortbay.jetty" % "jetty" % "6.1.17" % "test->default"
	val j = "org.mortbay.jetty" % "jsp-2.0" % "6.1.17" % "test->default"
	
	def indexURL = new java.net.URL("http://localhost:8080")
	def indexFile = new java.io.File("index.html")
	import Process._
	lazy val getPage = execTask { indexURL #> indexFile }
	
	lazy val checkPage = task { args => task { checkHelloWorld(args.mkString(" ")) } dependsOn getPage }
	
	private def checkHelloWorld(checkString: String) =
	{
		val value = xsbt.FileUtilities.read(indexFile)
		if(value.contains(checkString)) None else Some("index.html did not contain '" + checkString + "' :\n" +value)
	}
}