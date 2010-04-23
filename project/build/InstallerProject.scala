/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
import sbt._

abstract class InstallerProject(info: ProjectInfo) extends ParentProject(info) with NoPublish
{
	/** Project for the sbt plugin that a project uses to generate the installer jar. */
	lazy val installPlugin: InstallPluginProject = project("plugin", "Installer Plugin", new InstallPluginProject(_, installExtractor), installExtractor)
	/** Project for the code that runs when the generated installer jar is run. */
	lazy val installExtractor: InstallExtractProject = project("extract", "Installer Extractor", new InstallExtractProject(_, installPlugin))
}

import java.nio.charset.Charset

protected class InstallPluginProject(info: ProjectInfo, extract: => InstallExtractProject) extends PluginProject(info)
{
	private lazy val extractProject = extract
	override def mainResources = super.mainResources +++ extractProject.outputJar +++ extractLocation
	
	def extractLocation = (outputPath ##) / "extract.location"
	lazy val writeProperties = task { FileUtilities.write(extractLocation.asFile, extractProject.outputJar.relativePath, Charset.forName("UTF-8"), log) }
	override def packageAction = super.packageAction dependsOn(extractProject.proguard, writeProperties)
	
	override def deliverProjectDependencies = Nil
	val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
	Credentials(Path.fromFile(System.getProperty("user.home")) / ".ivy2" / ".credentials", log)
}