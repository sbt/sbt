import sbt._

class CompactifyTest(info: ProjectInfo) extends DefaultProject(info)
{
	def classes = (outputPath ** "*.class").get
	lazy val outputEmpty = task { if(classes.isEmpty) None else Some("Classes existed:\n\t" + classes.mkString("\n\t")) }
}