package sbt

import java.io.File

object ProguardProject
{
	val ProguardDescription = "Produces the final compacted jar that contains only the minimum classes needed using proguard."
	val WriteProguardDescription = "Creates the configuration file to use with proguard."
}
import ProguardProject._
trait ProguardProject extends BasicScalaProject
{
	def rawJarPath: Path
	def rawPackage: Task

	def proguardConfigurationPath: Path = outputPath / "proguard.pro"
	def outputJar: Path
	def rootProjectDirectory = rootProject.info.projectPath

	val toolsConfig = config("tools")
	val proguardJar = "net.sf.proguard" % "proguard" % "4.3" % "tools->default"

	lazy val proguard = proguardAction
	def proguardAction = proguardTask dependsOn(writeProguardConfiguration) describedAs(ProguardDescription)
	lazy val writeProguardConfiguration = writeProguardConfigurationAction
	def writeProguardConfigurationAction = writeProguardConfigurationTask dependsOn rawPackage describedAs WriteProguardDescription

	def basicOptions: Seq[String] =
		Seq(
				"-dontoptimize",
				"-dontobfuscate",
				"-dontnote",
				"-dontwarn",
				 "-ignorewarnings")
	def keepClasses: Seq[String] = Nil

	def mapInJars(inJars: Seq[File]): Seq[String] = inJars.map(f => "-injars " + mkpath(f))
	def mapLibraryJars(libraryJars: Seq[File]): Seq[String] = libraryJars.map(f => "-libraryjars " + mkpath(f))

	def template(inJars: Seq[File], libraryJars: Seq[File], outJar: File, options: Seq[String], mainClass: Option[String], keepClasses: Seq[String]) =
	{
		val keepMain =
			"""-keep public class %s {
				|    public static void main(java.lang.String[]);
				|}"""

		val lines =
			options ++
			keepClasses.map("-keep public class " + _  + " {\n public * ;\n}") ++
			mapInJars(inJars) ++
			 Seq("-injars " + mkpath(rawJarPath.asFile),
			"-outjars " + mkpath(outJar)) ++
			mapLibraryJars(libraryJars) ++
			 mainClass.map(main => keepMain.stripMargin.format(main)).toList
		lines.mkString("\n")
	}

	def mkpath(f: File) = '\"' + f.getAbsolutePath + '\"'
	private def proguardTask =
		task
		{
			FileUtilities.clean(outputJar :: Nil, log)
			val proguardClasspathString = Path.makeString(managedClasspath(toolsConfig).get)
			val configFile = proguardConfigurationPath.asFile.getAbsolutePath
			val exitValue = Process("java", List("-Xmx256M", "-cp", proguardClasspathString, "proguard.ProGuard", "@" + configFile)) ! log
			if(exitValue == 0) None else Some("Proguard failed with nonzero exit code (" + exitValue + ")")
		}
	private def writeProguardConfigurationTask =
		task
		{
			val externalDependencies = (mainCompileConditional.analysis.allExternals).map(_.getAbsoluteFile).filter(_.getName.endsWith(".jar"))
			log.debug("proguard configuration external dependencies: \n\t" + externalDependencies.mkString("\n\t"))
			// partition jars from the external jar dependencies of this project by whether they are located in the project directory
			// if they are, they are specified with -injars, otherwise they are specified with -libraryjars
			val (externalJars, libraryJars) = externalDependencies.toList.partition(jar => Path.relativize(rootProjectDirectory, jar).isDefined)
			log.debug("proguard configuration library jars locations: " + libraryJars.mkString(", "))

			val proguardConfiguration = template(externalJars, libraryJars, outputJar.asFile, basicOptions, getMainClass(false), keepClasses)
			log.debug("Proguard configuration written to " + proguardConfigurationPath)
			FileUtilities.write(proguardConfigurationPath.asFile, proguardConfiguration, log)
		}
}