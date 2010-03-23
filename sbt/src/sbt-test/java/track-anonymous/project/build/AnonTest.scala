import sbt._
class AnonTest(info: ProjectInfo) extends DefaultProject(info)
{
  override def compileOrder = CompileOrder.JavaThenScala
  lazy val checkOutput = task { args => println(args.mkString); checkOutputTask(args(0) == "exists") }
  private def checkOutputTask(shouldExist: Boolean) = 
	  task
	  {
		  if((mainCompilePath / "Anon.class").exists != shouldExist) Some("Top level class incorrect" )
		  else if( (mainCompilePath / "Anon$1.class").exists != shouldExist) Some("Inner class incorrect" )
		  else None
	  }
}