package xsbt.boot

import java.io.File

object Launch
{
	def apply(arguments: Seq[String]): Unit = apply( (new File("")).getAbsoluteFile, arguments )
	def apply(currentDirectory: File, arguments: Seq[String])
	{
		val (configFile, newArguments) = Configuration.find(arguments, currentDirectory)
		val parsed = Configuration.parse(configFile, currentDirectory)
		val (explicit, finish) = ResolveVersions(parsed)
		val launcher = new Launch(explicit)
		launch(launcher, explicit.getScalaVersion, explicit.app.toID, currentDirectory, newArguments, finish)
	}
	final def launch(launcher: xsbti.Launcher, scalaVersion: String, app: xsbti.ApplicationID, workingDirectory: File, arguments: Seq[String], finish: () => Unit)
	{
		val scalaProvider: xsbti.ScalaProvider = launcher.getScala(scalaVersion)
		val appProvider: xsbti.AppProvider = scalaProvider.app(app)
		val appConfig: xsbti.AppConfiguration = new AppConfiguration(arguments.toArray, workingDirectory, appProvider)

		val main = appProvider.newMain()
		finish()
		main.run(appConfig) match
		{
			case e: xsbti.Exit => System.exit(e.code)
			case r: xsbti.Reboot => launch(launcher, r.scalaVersion, r.app, r.baseDirectory, r.arguments, () => ())
			case x => throw new BootException("Invalid main result: " + x + (if(x eq null) "" else " (class: " + x.getClass + ")"))
		}
	}
}

import BootConfiguration.{appDirectoryName, baseDirectoryName, ScalaDirectoryName, TestLoadScalaClasses}
class Launch(val configuration: LaunchConfiguration) extends xsbti.Launcher
{
	import scala.collection.mutable.HashMap
	private val scalaProviders = new HashMap[String, ScalaProvider]
	def getScala(version: String): xsbti.ScalaProvider = scalaProviders.getOrElseUpdate(version, new ScalaProvider(version))

	class ScalaProvider(val version: String) extends xsbti.ScalaProvider with Provider
	{
		def launcher: xsbti.Launcher = Launch.this

		lazy val parentLoader = new BootFilteredLoader(getClass.getClassLoader)
		lazy val configuration = Launch.this.configuration.withScalaVersion(version)
		lazy val libDirectory = new File(configuration.boot.directory, baseDirectoryName(version))
		lazy val scalaHome = new File(libDirectory, ScalaDirectoryName)
		def baseDirectories = Seq(scalaHome)
		def testLoadClasses = TestLoadScalaClasses
		def target = UpdateTarget.UpdateScala
		def failLabel = "Scala " + version

		def app(id: xsbti.ApplicationID): xsbti.AppProvider = new AppProvider(id)

		class AppProvider(val id: xsbti.ApplicationID) extends xsbti.AppProvider with Provider
		{
			def scalaProvider: xsbti.ScalaProvider = ScalaProvider.this
			lazy val configuration = ScalaProvider.this.configuration.withApp(Application(id))
			lazy val appHome = new File(libDirectory, appDirectoryName(id, File.separator))
			def parentLoader = ScalaProvider.this.loader
			def baseDirectories = id.mainComponents.map(componentLocation) ++ Seq(appHome)
			def testLoadClasses = Seq(id.mainClass)
			def target = UpdateTarget.UpdateApp
			def failLabel = id.name + " " + id.version

			lazy val mainClass: Class[T] forSome { type T <: xsbti.AppMain } =
			{
				val c = Class.forName(id.mainClass, true, loader)
				c.asSubclass(classOf[xsbti.AppMain])
			}
			def newMain(): xsbti.AppMain = mainClass.newInstance

			def componentLocation(id: String): File = new File(appHome, id)
			def component(id: String) = GetJars(componentLocation(id) :: Nil)
			def defineComponent(id: String, files: Array[File]) =
			{
				val location = componentLocation(id)
				if(location.exists)
					throw new BootException("Cannot redefine component.  (id: " + id + ", files: " + files.mkString(","))
				else
					files.foreach(file => Copy(file, location))
			}
		}
	}
}