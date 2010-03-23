/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
import sbt._

class InstallerProject(info: ProjectInfo) extends ParentProject(info) with NoPublish
{
	/** Project for the sbt plugin that a project uses to generate the installer jar. */
	lazy val installPlugin: InstallPluginProject = project("plugin", "Installer Plugin", new InstallPluginProject(_, installExtractor), installExtractor)
	/** Project for the code that runs when the generated installer jar is run. */
	lazy val installExtractor: InstallExtractProject = project("extract", "Installer Extractor", new InstallExtractProject(_, installPlugin))
}

trait NoPublish extends BasicManagedProject
{
	override def publishLocalAction = publishAction
	override def deliverAction = publishAction
	override def deliverLocalAction = publishAction
	override def publishAction = task {None}
}