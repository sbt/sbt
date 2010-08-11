/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package xsbt.boot

import Pre._
import BootConfiguration.{CompilerModuleName, LibraryModuleName}
import java.io.File
import java.net.URL
import scala.collection.immutable.List

object Launch
{
	def apply(arguments: List[String]): Unit = apply( (new File("")).getAbsoluteFile , arguments )

	def apply(currentDirectory: File, arguments: List[String]): Unit =
		Configuration.find(arguments, currentDirectory) match { case (configLocation, newArguments) => configured(currentDirectory, configLocation, newArguments) }

	def configured(currentDirectory: File, configLocation: URL, arguments: List[String]): Unit =
	{
		val config = Configuration.parse(configLocation, currentDirectory)
		Find(config, currentDirectory) match { case (resolved, baseDirectory) => parsed(baseDirectory, resolved, arguments) }
	}
	def parsed(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Unit =
	{
		val propertiesFile = parsed.boot.properties
		import parsed.boot.{enableQuick, promptCreate, promptFill}
		if(isNonEmpty(promptCreate) && !propertiesFile.exists)
			Initialize.create(propertiesFile, promptCreate, enableQuick, parsed.appProperties)
		else if(promptFill)
			Initialize.fill(propertiesFile, parsed.appProperties)
		initialized(currentDirectory, parsed, arguments)
	}
	def initialized(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Unit =
	{
		parsed.logging.debug("Parsed configuration: " + parsed)
		val resolved = ResolveValues(parsed)
		resolved.logging.debug("Resolved configuration: " + resolved)
		explicit(currentDirectory, resolved, arguments)
	}

	def explicit(currentDirectory: File, explicit: LaunchConfiguration, arguments: List[String]): Unit =
		launch( run(Launcher(explicit)) ) (
			new RunConfiguration(explicit.getScalaVersion, explicit.app.toID, currentDirectory, arguments) )

	def run(launcher: xsbti.Launcher)(config: RunConfiguration): xsbti.MainResult =
	{
		import config._
		val scalaProvider: xsbti.ScalaProvider = launcher.getScala(scalaVersion)
		val appProvider: xsbti.AppProvider = scalaProvider.app(app)
		val appConfig: xsbti.AppConfiguration = new AppConfiguration(toArray(arguments), workingDirectory, appProvider)

		val main = appProvider.newMain()
		main.run(appConfig)
	}
	final def launch(run: RunConfiguration => xsbti.MainResult)(config: RunConfiguration)
	{
		run(config) match
		{
			case e: xsbti.Exit => System.exit(e.code)
			case r: xsbti.Reboot => launch(run)(new RunConfiguration(r.scalaVersion, r.app, r.baseDirectory, r.arguments.toList))
			case x => throw new BootException("Invalid main result: " + x + (if(x eq null) "" else " (class: " + x.getClass + ")"))
		}
	}
}
final class RunConfiguration(val scalaVersion: String, val app: xsbti.ApplicationID, val workingDirectory: File, val arguments: List[String]) extends NotNull

import BootConfiguration.{appDirectoryName, baseDirectoryName, ScalaDirectoryName, TestLoadScalaClasses}
class Launch private[xsbt](val bootDirectory: File, val ivyOptions: IvyOptions) extends xsbti.Launcher
{
	import ivyOptions.{cacheDirectory, classifiers, repositories}
	bootDirectory.mkdirs
	private val scalaProviders = new Cache[String, ScalaProvider](new ScalaProvider(_))
	def getScala(version: String): xsbti.ScalaProvider = scalaProviders(version)

	lazy val topLoader = new BootFilteredLoader(getClass.getClassLoader)
	val updateLockFile = new File(bootDirectory, "sbt.boot.lock")

	def globalLock: xsbti.GlobalLock = Locks

	class ScalaProvider(val version: String) extends xsbti.ScalaProvider with Provider
	{
		def launcher: xsbti.Launcher = Launch.this
		def parentLoader = topLoader

		lazy val configuration = new UpdateConfiguration(bootDirectory, cacheDirectory, version, repositories)
		lazy val libDirectory = new File(configuration.bootDirectory, baseDirectoryName(version))
		lazy val scalaHome = new File(libDirectory, ScalaDirectoryName)
		def compilerJar = new File(scalaHome,CompilerModuleName + ".jar")
		def libraryJar = new File(scalaHome, LibraryModuleName + ".jar")
		override def classpath = array(compilerJar, libraryJar)
		def baseDirectories = List(scalaHome)
		def testLoadClasses = TestLoadScalaClasses
		def target = new UpdateScala(Value.get(classifiers.forScala))
		def failLabel = "Scala " + version
		def lockFile = updateLockFile
		def extraClasspath = array()

		def app(id: xsbti.ApplicationID): xsbti.AppProvider = new AppProvider(id)

		class AppProvider(val id: xsbti.ApplicationID) extends xsbti.AppProvider with Provider
		{
			def scalaProvider: xsbti.ScalaProvider = ScalaProvider.this
			def configuration = ScalaProvider.this.configuration
			lazy val appHome = new File(libDirectory, appDirectoryName(id, File.separator))
			def parentLoader = ScalaProvider.this.loader
			def baseDirectories = appHome :: id.mainComponents.map(components.componentLocation).toList
			def testLoadClasses = List(id.mainClass)
			def target = new UpdateApp(Application(id), Value.get(classifiers.app))
			def failLabel = id.name + " " + id.version
			def lockFile = updateLockFile
			def mainClasspath = fullClasspath
			def extraClasspath = id.classpathExtra

			lazy val mainClass: Class[T] forSome { type T <: xsbti.AppMain } =
			{
				val c = Class.forName(id.mainClass, true, loader)
				c.asSubclass(classOf[xsbti.AppMain])
			}
			def newMain(): xsbti.AppMain = mainClass.newInstance

			lazy val components = new ComponentProvider(appHome)
		}
	}
}
object Launcher
{
	def apply(bootDirectory: File, repositories: List[Repository]): xsbti.Launcher =
		apply(bootDirectory, IvyOptions(None, Classifiers(Nil, Nil), repositories))
	def apply(bootDirectory: File, ivyOptions: IvyOptions): xsbti.Launcher =
		apply(bootDirectory, ivyOptions, GetLocks.find)
	def apply(bootDirectory: File, ivyOptions: IvyOptions, locks: xsbti.GlobalLock): xsbti.Launcher =
		new Launch(bootDirectory, ivyOptions) {
			override def globalLock = locks
		}
	def apply(explicit: LaunchConfiguration): xsbti.Launcher =
		new Launch(explicit.boot.directory, explicit.ivyConfiguration)
	def defaultAppProvider(baseDirectory: File): xsbti.AppProvider = getAppProvider(baseDirectory, Configuration.configurationOnClasspath)
	def getAppProvider(baseDirectory: File, configLocation: URL): xsbti.AppProvider =
	{
		val parsed = ResolvePaths(Configuration.parse(configLocation, baseDirectory), baseDirectory)
		Initialize.process(parsed.boot.properties, parsed.appProperties, Initialize.selectQuick)
		val config = ResolveValues(parsed)
		val launcher = apply(config)
		val scalaProvider = launcher.getScala(config.getScalaVersion)
		scalaProvider.app(config.app.toID)
	}
}
class ComponentProvider(baseDirectory: File) extends xsbti.ComponentProvider
{
	def componentLocation(id: String): File = new File(baseDirectory, id)
	def component(id: String) = Provider.wrapNull(componentLocation(id).listFiles).filter(_.isFile)
	def defineComponent(id: String, files: Array[File]) =
	{
		val location = componentLocation(id)
		if(location.exists)
			throw new BootException("Cannot redefine component.  ID: " + id + ", files: " + files.mkString(","))
		else
			Copy(files.toList, location)
	}
	def lockFile = ComponentProvider.lockFile(baseDirectory)
}
object ComponentProvider
{
	def lockFile(baseDirectory: File) =
	{
		baseDirectory.mkdirs()
		new File(baseDirectory, "sbt.components.lock")
	}
}