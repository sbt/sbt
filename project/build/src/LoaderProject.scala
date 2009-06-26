import sbt._

import LoaderProject._
import java.io.File

// a project for the sbt launcher
// the main content of this project definition is setting up and running proguard
//   to combine and compact all dependencies into a single jar
protected/* removes the ambiguity as to which project is the entry point by making this class non-public*/
	class LoaderProject(info: ProjectInfo) extends DefaultProject(info)
{
	val mainClassName = "sbt.boot.Boot"
	val baseName = "sbt-launcher"
	val proguardConfigurationPath: Path = outputPath / "proguard.pro"
	lazy val outputJar: Path = rootProject.outputPath / (baseName + "-" + version + ".jar")
	def rootProjectDirectory = rootProject.info.projectPath
	
	override def mainClass = Some(mainClassName)
	override def defaultJarBaseName = baseName + "-" + version.toString
	
	/******  Resources *****/
	def extraResources = descendents(info.projectPath / "licenses", "*") +++ "LICENSE" +++ "NOTICE"
	override def mainResources = super.mainResources +++ extraResources
	
	/****** Dependencies  *******/
	val defaultConfig = config("default")
	val toolsConfig = config("tools")
	val ivy = "org.apache.ivy" % "ivy" % "2.0.0"
	val proguardJar = "net.sf.proguard" % "proguard" % "4.3" % "tools->default"
	
	/******** Proguard *******/
	lazy val proguard = proguardTask dependsOn(`package`, writeProguardConfiguration) describedAs(ProguardDescription)
	lazy val writeProguardConfiguration = writeProguardConfigurationTask dependsOn `package` describedAs WriteProguardDescription
	
	private def proguardTask =
		task
		{
			FileUtilities.clean(outputJar :: Nil, log)
			val proguardClasspath = managedClasspath(toolsConfig)
			val proguardClasspathString = Path.makeString(proguardClasspath.get)
			val configFile = proguardConfigurationPath.asFile.getAbsolutePath
			val exitValue = Process("java", List("-Xmx128M", "-cp", proguardClasspathString, "proguard.ProGuard", "@" + configFile)) ! log
			if(exitValue == 0) None else Some("Proguard failed with nonzero exit code (" + exitValue + ")")
		}
	private def writeProguardConfigurationTask =
		task
		{
			// these are classes that need to be explicitly kept because they are loaded reflectively
			val ivyKeepResolvers =
				"org.apache.ivy.plugins.resolver.URLResolver" ::
				"org.apache.ivy.plugins.resolver.IBiblioResolver" ::
				Nil
			// the template for the proguard configuration file
			val outTemplate = """
				|-dontoptimize
				|-dontobfuscate
				|-dontnote
				|-dontwarn
				|-libraryjars %s
				|-injars %s(!META-INF/**,!fr/**,!**/antlib.xml,!**/*.png)
				|-injars %s(!META-INF/**)
				|%s
				|-outjars %s
				|-ignorewarnings
				|%s
				|%s
				|-keep public class %s {
				|    public static void main(java.lang.String[]);
				|}"""
			
			val defaultJar = (outputPath / defaultJarName).asFile.getAbsolutePath
			log.debug("proguard configuration using main jar " + defaultJar)
			val ivyKeepOptions = ivyKeepResolvers.map("-keep public class " + _  + allPublic).mkString("\n")
			val runtimeClasspath = runClasspath.get.map(_.asFile).toList
			val jlineJars = runtimeClasspath.filter(isJLineJar)
			val externalDependencies = (mainCompileConditional.analysis.allExternals).map(_.getAbsoluteFile).filter(_.getName.endsWith(".jar"))
			log.debug("proguard configuration external dependencies: \n\t" + externalDependencies.mkString("\n\t"))
			// partition jars from the external jar dependencies of this project by whether they are located in the project directory
			// if they are, they are specified with -injars, otherwise they are specified with -libraryjars
			val (externalJars, libraryJars) = externalDependencies.toList.partition(jar => Path.relativize(rootProjectDirectory, jar).isDefined)
			log.debug("proguard configuration library jars locations: " + libraryJars.mkString(", "))
			// pull out Ivy in order to exclude resources inside
			val (ivyJars, externalJarsNoIvy) = externalJars.partition(_.getName.startsWith("ivy"))
			log.debug("proguard configuration ivy jar location: " + ivyJars.mkString(", "))
			// the loader uses JLine, so there is a dependency on the compiler (because JLine is distributed with the compiler,
			//   it finds the JLine classes from the compiler jar instead of the jline jar on the classpath), but we don't want to
			//    include the version of JLine from the compiler.
			val includeExternalJars = externalJarsNoIvy.filter(jar => !isJarX(jar, "scala-compiler"))
			// exclude properties files and manifests from scala-library jar
			val inJars = (defaultJar :: includeExternalJars.map( _ + "(!META-INF/**,!*.properties)")).map("-injars " + _).mkString("\n")
			
			withJar(ivyJars, "Ivy") { ivyJar =>
				withJar(jlineJars, "JLine") { jlineJar =>
					val proguardConfiguration =
						outTemplate.stripMargin.format(libraryJars.mkString(File.pathSeparator),
							ivyJar.getAbsolutePath, jlineJar.getAbsolutePath,
							inJars, outputJar.absolutePath, ivyKeepOptions, keepJLine, mainClassName)
					log.debug("Proguard configuration written to " + proguardConfigurationPath)
					FileUtilities.write(proguardConfigurationPath.asFile, proguardConfiguration, log)
				}
			}
		}
	private def withJar(files: List[File], name: String)(f: File => Option[String]): Option[String] =
		files match
		{
			case Nil => Some(name + " not present (try running update)")
			case jar :: _ => f(jar)
		}
	private def isJLineJar(file: File) = isJarX(file, "jline")
	private def isJarX(file: File, x: String) =
	{
		val name = file.getName
		name.startsWith(x) && name.endsWith(".jar")
	}
	// class body declaration for proguard that keeps all public members
	private val allPublic = " {\n public * ;\n}"
	
	private val keepJLine =
	"""
	|-keep public class jline.** {
	|    public protected *;
	|}
	""".stripMargin
}
object LoaderProject
{
	val ProguardDescription = "Produces the final compacted jar that contains only the minimum classes needed using proguard."
	val WriteProguardDescription = "Creates the configuration file to use with proguard."
}