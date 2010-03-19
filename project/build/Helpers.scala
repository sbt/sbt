import sbt._

trait NoPublish extends ManagedBase
{
	override final def publishAction = task { None }
	override final def deliverAction = publishAction
}
trait NoCrossPaths extends Project
{
	override def disableCrossPaths = true
}
trait JavaProject extends BasicScalaProject with NoCrossPaths
{
	// ensure that interfaces are only Java sources and that they cannot reference Scala classes
	override def mainSources = descendents(mainSourceRoots, "*.java")
	override def compileOrder = CompileOrder.JavaThenScala
}
trait SourceProject extends BasicScalaProject with NoCrossPaths
{
	override def packagePaths = mainResources +++ mainSources // the default artifact is a jar of the main sources and resources
}
trait ManagedBase extends BasicScalaProject
{
	override def deliverScalaDependencies = Nil
	override def managedStyle = ManagedStyle.Ivy
	override def useDefaultConfigurations = false
	val defaultConf = Configurations.Default
	val testConf = Configurations.Test
}
trait Component extends DefaultProject
{
	override def projectID = componentID match { case Some(id) => super.projectID extra("e:component" -> id); case None => super.projectID }
	def componentID: Option[String] = None
}