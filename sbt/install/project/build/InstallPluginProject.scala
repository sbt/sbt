/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */

import sbt._
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