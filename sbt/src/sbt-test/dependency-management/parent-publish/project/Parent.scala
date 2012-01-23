	import sbt._

object ParentTest extends Build
{
	lazy val parent: Project = Project("Flowmodel", file(".")) aggregate(core, reporters)
	lazy val core: Project = Project("Flowmodel-core", file("core"), delegates = parent :: Nil)
	lazy val reporters: Project = Project("Extra-reporters", file("reporters"), delegates = parent :: Nil) aggregate(jfreechart) dependsOn(jfreechart)
	lazy val jfreechart: Project = Project("JFreeChart-reporters", file("jfreechart")/*, delegates = reporters :: Nil*/) dependsOn(core)
}
