import sbt._

// verify that javaCompileOptions are used
class JavaProject(info: ProjectInfo) extends DefaultProject(info)
{
	// make the source target 1.4 so that we get an error when these options are used
	override def javaCompileOptions = ("-source" :: "1.4" :: Nil).map(JavaCompileOption(_))
}