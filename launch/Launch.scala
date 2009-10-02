package xsbt.boot

import java.io.File
import java.net.URL

object Launch
{
	def apply(arguments: Seq[String]): Unit = apply( (new File("")).getAbsoluteFile , arguments )

	def apply(currentDirectory: File, arguments: Seq[String]): Unit =
		Configuration.find(arguments, currentDirectory) match { case (configLocation, newArguments) => configured(currentDirectory, configLocation, newArguments) }

	def configured(currentDirectory: File, configLocation: URL, arguments: Seq[String]): Unit =
	{
		val config = Configuration.parse(configLocation, currentDirectory)
		Find(config, currentDirectory) match { case (resolved, baseDirectory) => parsed(baseDirectory, resolved, arguments) }
	}

	def parsed(currentDirectory: File, parsed: LaunchConfiguration, arguments: Seq[String]): Unit =
		ResolveVersions(parsed) match { case (resolved, finish) => explicit(currentDirectory, resolved, arguments, finish) }

	def explicit(currentDirectory: File, explicit: LaunchConfiguration, arguments: Seq[String], setupComplete: () => Unit): Unit =
		launch( run(new Launch(explicit.boot.directory, explicit.repositories)) ) (
			RunConfiguration(explicit.getScalaVersion, explicit.app.toID, currentDirectory, arguments, setupComplete) )

	def run(launcher: xsbti.Launcher)(config: RunConfiguration): xsbti.MainResult =
	{
		import config._
		val scalaProvider: xsbti.ScalaProvider = launcher.getScala(scalaVersion)
		val appProvider: xsbti.AppProvider = scalaProvider.app(app)
		val appConfig: xsbti.AppConfiguration = new AppConfiguration(arguments.toArray, workingDirectory, appProvider)

		val main = appProvider.newMain()
		setupComplete()
		main.run(appConfig)
	}
	final def launch(run: RunConfiguration => xsbti.MainResult)(config: RunConfiguration)
	{
		run(config) match
		{
			case e: xsbti.Exit => System.exit(e.code)
			case r: xsbti.Reboot => launch(run)(RunConfiguration(r.scalaVersion, r.app, r.baseDirectory, r.arguments, () => ()))
			case x => throw new BootException("Invalid main result: " + x + (if(x eq null) "" else " (class: " + x.getClass + ")"))
		}
	}
}
case class RunConfiguration(scalaVersion: String, app: xsbti.ApplicationID, workingDirectory: File, arguments: Seq[String], setupComplete: () => Unit) extends NotNull

import BootConfiguration.{appDirectoryName, baseDirectoryName, ScalaDirectoryName, TestLoadScalaClasses}
class Launch(val bootDirectory: File, repositories: Seq[Repository]) extends xsbti.Launcher
{
	import scala.collection.mutable.HashMap
	private val scalaProviders = new HashMap[String, ScalaProvider]
	def getScala(version: String): xsbti.ScalaProvider = scalaProviders.getOrElseUpdate(version, new ScalaProvider(version))

	class ScalaProvider(val version: String) extends xsbti.ScalaProvider with Provider
	{
		def launcher: xsbti.Launcher = Launch.this

		lazy val parentLoader = new BootFilteredLoader(getClass.getClassLoader)

		lazy val configuration = new UpdateConfiguration(bootDirectory, version, repositories)
		lazy val libDirectory = new File(configuration.bootDirectory, baseDirectoryName(version))
		lazy val scalaHome = new File(libDirectory, ScalaDirectoryName)
		def compilerJar = new File(scalaHome, "scala-compiler.jar")
		def libraryJar = new File(scalaHome, "scala-library.jar")
		def baseDirectories = Seq(scalaHome)
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
			def baseDirectories = id.mainComponents.map(components.componentLocation) ++ Seq(appHome)
			def testLoadClasses = Seq(id.mainClass)
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
			Copy(files, location)
	}
}