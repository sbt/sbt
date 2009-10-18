package xsbt.boot

import Pre._
import java.io.File
import java.net.URL

object Launch
{
	val start = System.currentTimeMillis
	def time(label: String) = System.out.println(label + " : " + (System.currentTimeMillis - start) / 1000.0 + " s")
	def apply(arguments: List[String]): Unit = apply( (new File("")).getAbsoluteFile , arguments )

	def apply(currentDirectory: File, arguments: List[String]): Unit =
		Configuration.find(arguments, currentDirectory) match { case (configLocation, newArguments) => configured(currentDirectory, configLocation, newArguments) }

	def configured(currentDirectory: File, configLocation: URL, arguments: List[String]): Unit =
	{
		time("found boot config")
		val config = Configuration.parse(configLocation, currentDirectory)
		time("parsed")
		Find(config, currentDirectory) match { case (resolved, baseDirectory) => parsed(baseDirectory, resolved, arguments) }
	}
	def parsed(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Unit =
	{
		time("found working directory")
		val propertiesFile = parsed.boot.properties
		import parsed.boot.{enableQuick, promptCreate, promptFill}
		if(isNonEmpty(promptCreate) && !propertiesFile.exists)
			Initialize.create(propertiesFile, promptCreate, enableQuick, parsed.appProperties)
		else if(promptFill)
			Initialize.fill(propertiesFile, parsed.appProperties)
		time("initialized")
		initialized(currentDirectory, parsed, arguments)
	}
	def initialized(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Unit =
	{
		val resolved = ResolveVersions(parsed)
		time("resolved")
		explicit(currentDirectory, resolved, arguments)
	}

	def explicit(currentDirectory: File, explicit: LaunchConfiguration, arguments: List[String]): Unit =
		launch( run(new Launch(explicit.boot.directory, explicit.repositories)) ) (
			new RunConfiguration(explicit.getScalaVersion, explicit.app.toID, currentDirectory, arguments) )

	def run(launcher: xsbti.Launcher)(config: RunConfiguration): xsbti.MainResult =
	{
		import config._
		val scalaProvider: xsbti.ScalaProvider = launcher.getScala(scalaVersion)
		val appProvider: xsbti.AppProvider = scalaProvider.app(app)
		val appConfig: xsbti.AppConfiguration = new AppConfiguration(toArray(arguments), workingDirectory, appProvider)

		time("pre-load")
		val main = appProvider.newMain()
		time("loaded")
		val result = main.run(appConfig)
		time("ran")
		result
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
class Launch(val bootDirectory: File, repositories: List[Repository]) extends xsbti.Launcher
{
	private val scalaProviders = new Cache[String, ScalaProvider](new ScalaProvider(_))
	def getScala(version: String): xsbti.ScalaProvider = scalaProviders(version)

	lazy val topLoader = new BootFilteredLoader(getClass.getClassLoader)

	def globalLock: xsbti.GlobalLock = Locks

	class ScalaProvider(val version: String) extends xsbti.ScalaProvider with Provider
	{
		def launcher: xsbti.Launcher = Launch.this
		def parentLoader = topLoader

		lazy val configuration = new UpdateConfiguration(bootDirectory, version, repositories)
		lazy val libDirectory = new File(configuration.bootDirectory, baseDirectoryName(version))
		lazy val scalaHome = new File(libDirectory, ScalaDirectoryName)
		def compilerJar = new File(scalaHome, "scala-compiler.jar")
		def libraryJar = new File(scalaHome, "scala-library.jar")
		def baseDirectories = List(scalaHome)
		def testLoadClasses = TestLoadScalaClasses
		def target = UpdateScala
		def failLabel = "Scala " + version

		def app(id: xsbti.ApplicationID): xsbti.AppProvider = new AppProvider(id)

		class AppProvider(val id: xsbti.ApplicationID) extends xsbti.AppProvider with Provider
		{
			def scalaProvider: xsbti.ScalaProvider = ScalaProvider.this
			def configuration = ScalaProvider.this.configuration
			lazy val appHome = new File(libDirectory, appDirectoryName(id, File.separator))
			def parentLoader = ScalaProvider.this.loader
			def baseDirectories = appHome :: id.mainComponents.map(components.componentLocation).toList
			def testLoadClasses = List(id.mainClass)
			def target = new UpdateApp(Application(id))
			def failLabel = id.name + " " + id.version

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
class ComponentProvider(baseDirectory: File) extends xsbti.ComponentProvider
{
	def componentLocation(id: String): File = new File(baseDirectory, id)
	def component(id: String) = GetJars.wrapNull(componentLocation(id).listFiles).filter(_.isFile)
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