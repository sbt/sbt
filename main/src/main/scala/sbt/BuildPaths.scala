/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import KeyRanks.DSetting

object BuildPaths
{
	val globalBaseDirectory = AttributeKey[File]("global-base-directory", "The base directory for global sbt configuration and staging.", DSetting)
	val globalPluginsDirectory = AttributeKey[File]("global-plugins-directory", "The base directory for global sbt plugins.", DSetting)
	val globalSettingsDirectory = AttributeKey[File]("global-settings-directory", "The base directory for global sbt settings.", DSetting)
	val stagingDirectory = AttributeKey[File]("staging-directory", "The directory for staging remote projects.", DSetting)

	import Path._

	def getGlobalBase(state: State): File =
		getFileSetting(globalBaseDirectory, GlobalBaseProperty, defaultGlobalBase)(state)

	def getStagingDirectory(state: State, globalBase: File): File =
		 getFileSetting(stagingDirectory, StagingProperty, defaultStaging(globalBase))(state)

	def getGlobalPluginsDirectory(state: State, globalBase: File): File =
		 getFileSetting(globalPluginsDirectory, GlobalPluginsProperty, defaultGlobalPlugins(globalBase))(state)

	def getGlobalSettingsDirectory(state: State, globalBase: File): File =
		 getFileSetting(globalSettingsDirectory, GlobalSettingsProperty, globalBase)(state)

	def getFileSetting(stateKey: AttributeKey[File], property: String, default: File)(state: State): File =
		state get stateKey orElse getFileProperty(property) getOrElse default

	def getFileProperty(name: String): Option[File] = Option(System.getProperty(name)) flatMap { path =>
		if(path.isEmpty) None else Some(new File(path))
	}
		
	def defaultGlobalBase = Path.userHome / ConfigDirectoryName
	private[this] def defaultStaging(globalBase: File) = globalBase / "staging"
	private[this] def defaultGlobalPlugins(globalBase: File) = globalBase / PluginsDirectoryName
	
	def configurationSources(base: File): Seq[File] = (base * (GlobFilter("*.sbt") - ".sbt")).get
	def pluginDirectory(definitionBase: File) = definitionBase / PluginsDirectoryName

	def evalOutputDirectory(base: File) = outputDirectory(base) / "config-classes"
	def outputDirectory(base: File) = base / DefaultTargetName

	def projectStandard(base: File) = base / "project"
	def projectHidden(base: File) = base / ConfigDirectoryName
	def selectProjectDir(base: File, log: Logger) =
	{
		val a = projectHidden(base)
		val b = projectStandard(base)
		if(a.exists)
		{
			log.warn("Alternative project directory " + ConfigDirectoryName + " (" + a + ") has been deprecated since sbt 0.12.0.\n  Please use the standard location: " + b)
			a
		}
		else b
	}

	final val PluginsDirectoryName = "plugins"
	final val DefaultTargetName = "target"
	final val ConfigDirectoryName = ".sbt"
	final val GlobalBaseProperty = "sbt.global.base"
	final val StagingProperty = "sbt.global.staging"
	final val GlobalPluginsProperty = "sbt.global.plugins"
	final val GlobalSettingsProperty = "sbt.global.settings"

	def crossPath(base: File, instance: xsbti.compile.ScalaInstance): File = base / ("scala_" + instance.version)
}
