import sbt._

class CompactifyTest(info: ProjectInfo) extends DefaultProject(info)
{
	def classes = (outputDirectory ** "*.class").get
	lazy val outputEmpty = task { if(!classes.isEmpty) error("Classes existed:\n\t" + classes.mkString("\n\t")) }
}
