/* sbt -- Simple Build Tool
 * Copyright 2009, 2010 Mark Harrah
 */

import sbt._
import java.io.File

// TODO: use ProguardProject
protected class InstallExtractProject(info: ProjectInfo, pluginProject: => InstallPluginProject) extends DefaultProject(info) with NoPublish with ProguardProject
{
	override def unmanagedClasspath = super.unmanagedClasspath +++ info.sbtClasspath
	override def rawPackage = `package`
	override def rawJarPath = jarPath

	private lazy val plugin = pluginProject
	val mainClassName = "sbt.extract.Main"
	val defaultConfig = Configurations.Default
	def outputJar = (plugin.outputPath ##) / defaultJarName
	override val proguardConfigurationPath: Path = outputPath / "proguard.pro"

	/******** Proguard  *******/
	override lazy val proguard = proguardTask dependsOn(`package`, writeProguardConfiguration, cleanProguard)
	override lazy val writeProguardConfiguration = writeProguardConfigurationTask dependsOn `package`
	lazy val cleanProguard = cleanTask(outputJar)

	override protected def writeProguardConfigurationTask =
		task
		{
			// the template for the proguard configuration file
			val outTemplate = """
				|-dontoptimize
				|-dontobfuscate
				|-dontnote
				|-dontwarn
				|-libraryjars "%s"
				|%s
				|-outjars "%s"
				|-ignorewarnings
				|-keep public class %s {
				|    public static void main(java.lang.String[]);
				|}"""

			val defaultJar = mkpath(jarPath.absolutePath, '\"')
			log.debug("proguard configuration using main jar " + defaultJar)
			val externalDependencies = (mainCompileConditional.analysis.allExternals).map(_.getAbsoluteFile).filter(_.getName.endsWith(".jar"))
			debugJars("external dependencies", externalDependencies)
			// partition jars from the external jar dependencies of this project by whether they are located in the project directory
			// if they are, they are specified with -injars, otherwise they are specified with -libraryjars
			val (externalJars, libraryJars) = externalDependencies.toList.partition{jar => Path.relativize(rootProjectDirectory, jar).isDefined}
			debugJars("library jars", libraryJars)
			val externalJarStrings = externalJars.map( mkpath(_) + "(!META-INF/**,!*.properties)")
			// exclude properties files and manifests from scala-library jar
			val inJars = (defaultJar :: externalJarStrings).map("-injars " + _).mkString("\n")

			val proguardConfiguration = outTemplate.stripMargin.format(libraryJars.mkString(File.pathSeparator), inJars, outputJar.absolutePath, mainClassName)
			log.debug("Proguard configuration written to " + proguardConfigurationPath)
			FileUtilities.write(proguardConfigurationPath.asFile, proguardConfiguration, log)
		}
	
	private def debugJars[T](label: String, jars: Iterable[T]): Unit =
		log.debug("proguard configuration " + label + ": \n\t" + jars.mkString("\n\t"))
}