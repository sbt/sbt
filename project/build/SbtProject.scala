/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah
 */
import sbt._

import java.io.File

class SbtProject(info: ProjectInfo) extends ParentProject(info)
{
	// Launcher sub project.
	lazy val boot = project("boot", "Simple Build Tool Loader", new LoaderProject(_))
	// Main builder sub project
	lazy val main = project(info.projectPath, "Simple Build Tool", new MainProject(_))
	// One-shot build for users building from trunk
	lazy val fullBuild = task { None } dependsOn(boot.proguard, main.crossPublishLocal) describedAs
		"Builds the loader and builds main sbt against all supported versions of Scala and installs to the local repository."
		
	override def shouldCheckOutputDirectories = false
	override def baseUpdateOptions = QuietUpdate :: Nil
	
	//override def parallelExecution = true
	override def deliverLocalAction = noAction
	private def noAction = task { None }
	override def publishLocalAction = noAction
}

protected class MainProject(val info: ProjectInfo) extends CrossCompileProject with test.ScalaScripted
{
	override def mainSources =
		if(ScalaVersion.currentString.startsWith("2.8")) // cannot compile against test libraries currently
			Path.lazyPathFinder { super.mainSources.get.filter(!_.asFile.getName.endsWith("TestFrameworkImpl.scala")) }
		else
			super.mainSources
	override def defaultJarBaseName = "sbt_" + version.toString
	/** Additional resources to include in the produced jar.*/
	def extraResources = descendents(info.projectPath / "licenses", "*") +++ "LICENSE" +++ "NOTICE"
	override def mainResources = super.mainResources +++ extraResources
	override def mainClass = Some("sbt.Main")
	override def testOptions = ExcludeTests("sbt.ReflectiveSpecification" :: "sbt.ProcessSpecification" :: Nil) :: super.testOptions.toList
	override def scriptedDependencies = testCompile :: `package` :: Nil
}