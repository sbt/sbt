import sbt._

class DefineColor(info: ProjectInfo) extends DefaultProject(info)
{
	override def projectID = super.projectID extra("e:color" -> "red")
}