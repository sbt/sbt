import sbt._

class FlowmodelProject(info: ProjectInfo) extends ParentProject(info)
{
  lazy val core = project("core", "Flowmodel core")
  lazy val reporters = project("reporters", "Extra reporters", new Reporters(_))

  class Reporters(info: ProjectInfo) extends ParentProject(info)
  {
    lazy val jfreechart = project("jfreechart", "JFreeChart reporters", core)
  }
}