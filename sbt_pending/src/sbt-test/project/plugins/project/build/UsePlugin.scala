import sbt._

class UsePlugin(info: ProjectInfo) extends DefaultProject(info)
{
	import antlr.Tool // verify that antlr is on compile classpath
	lazy val check = task { Class.forName("antlr.Tool"); None } // verify antlr is on runtime classpath
}