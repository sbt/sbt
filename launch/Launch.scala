/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package xsbt.boot

import Pre._
import BootConfiguration.{CompilerModuleName, LibraryModuleName}
import java.io.File
import java.net.URL
import scala.collection.immutable.List

object Launch
{
	def apply(arguments: List[String]): Option[Int] = apply( (new File("")).getAbsoluteFile , arguments )

	def apply(currentDirectory: File, arguments: List[String]): Option[Int] =
		Configuration.find(arguments, currentDirectory) match { case (configLocation, newArguments) => configured(currentDirectory, configLocation, newArguments) }

	def configured(currentDirectory: File, configLocation: URL, arguments: List[String]): Option[Int] =
	{
		val config = Configuration.parse(configLocation, currentDirectory)
		Find(config, currentDirectory) match { case (resolved, baseDirectory) => parsed(baseDirectory, resolved, arguments) }
	}
	def parsed(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Option[Int] =
	{
		val propertiesFile = parsed.boot.properties
		import parsed.boot.{enableQuick, promptCreate, promptFill}
		if(isNonEmpty(promptCreate) && !propertiesFile.exists)
			Initialize.create(propertiesFile, promptCreate, enableQuick, parsed.appProperties)
		else if(promptFill)
			Initialize.fill(propertiesFile, parsed.appProperties)
		initialized(currentDirectory, parsed, arguments)
	}
	def initialized(currentDirectory: File, parsed: LaunchConfiguration, arguments: List[String]): Option[Int] =
	{
		parsed.logging.debug("Parsed configuration: " + parsed)
		val resolved = ResolveValues(parsed)
		resolved.logging.debug("Resolved configuration: " + resolved)
		explicit(currentDirectory, resolved, arguments)
	}

	def explicit(currentDirectory: File, explicit: LaunchConfiguration, arguments: List[String]): Option[Int] =
		launch( run(Launcher(explicit)) ) (
			new RunConfiguration(explicit.getScalaVersion, explicit.app.toID, currentDirectory, arguments) )

	def run(launcher: xsbti.Launcher)(config: RunConfiguration): xsbti.MainResult =
	{
		import config._
		val scalaProvider: xsbti.ScalaProvider = launcher.getScala(scalaVersion, "(for " + app.name + ")")
		val appProvider: xsbti.AppProvider = scalaProvider.app(app)
		val appConfig: xsbti.AppConfiguration = new AppConfiguration(toArray(arguments), workingDirectory, appProvider)

		val main = appProvider.newMain()
		try { main.run(appConfig) }
		catch { case e: xsbti.FullReload => if(e.clean) delete(launcher.bootDirectory); throw e }
	}
	private[this] def delete(f: File)
	{
		if(f.isDirectory)
		{
			val fs = f.listFiles()
			if(fs ne null) fs foreach delete
		}
		if(f.exists) f.delete()
	}
	final def launch(run: RunConfiguration => xsbti.MainResult)(config: RunConfiguration): Option[Int] =
	{
		run(config) match
		{
			case e: xsbti.Exit => Some(e.code)
			case c: xsbti.Continue => None
			case r: xsbti.Reboot => launch(run)(new RunConfiguration(r.scalaVersion, r.app, r.baseDirectory, r.arguments.toList))
			case x => throw new BootException("Invalid main result: " + x + (if(x eq null) "" else " (class: " + x.getClass + ")"))
		}
	}
}
final class RunConfiguration(val scalaVersion: String, val app: xsbti.ApplicationID, val workingDirectory: File, val arguments: List[String])

import BootConfiguration.{appDirectoryName, baseDirectoryName, ScalaDirectoryName, TestLoadScalaClasses}
class Launch private[xsbt](val bootDirectory: File, val lockBoot: Boolean, val ivyOptions: IvyOptions) extends xsbti.Launcher
{
	import ivyOptions.{checksums => checksumsList, classifiers, repositories}
	bootDirectory.mkdirs
	private val scalaProviders = new Cache[String, String, ScalaProvider](new ScalaProvider(_, _))
	def getScala(version: String): xsbti.ScalaProvider = getScala(version, "")
	def getScala(version: String, reason: String): xsbti.ScalaProvider = scalaProviders(version, reason)

	lazy val topLoader = (new JNAProvider).loader
	val updateLockFile = if(lockBoot) Some(new File(bootDirectory, "sbt.boot.lock")) else None

	def globalLock: xsbti.GlobalLock = Locks
	def ivyHome = ivyOptions.ivyHome.orNull
	def ivyRepositories = repositories.toArray
	def checksums = checksumsList.toArray[String]

	class JNAProvider extends Provider
	{
		lazy val id = new Application("net.java.dev.jna", "jna", new Explicit("3.2.7"), "", Nil, false, array())
		lazy val configuration = new UpdateConfiguration(bootDirectory, ivyOptions.ivyHome, "", repositories, checksumsList)
		lazy val libDirectory = new File(bootDirectory, baseDirectoryName(""))
		def baseDirectories: List[File] = new File(libDirectory, appDirectoryName(id.toID, File.separator)) :: Nil
		def testLoadClasses: List[String] = "com.sun.jna.Function" :: Nil
		def extraClasspath = array()
		def target = new UpdateApp(id, Nil)
		lazy val parentLoader = new BootFilteredLoader(getClass.getClassLoader)
		def failLabel = "JNA"
		def lockFile = updateLockFile
	}

	class ScalaProvider(val version: String, override val reason: String) extends xsbti.ScalaProvider with Provider
	{
		def launcher: xsbti.Launcher = Launch.this
		def parentLoader = topLoader

		lazy val configuration = new UpdateConfiguration(bootDirectory, ivyOptions.ivyHome, version, repositories, checksumsList)
		lazy val libDirectory = new File(configuration.bootDirectory, baseDirectoryName(version))
		lazy val scalaHome = new File(libDirectory, ScalaDirectoryName)
		def compilerJar = new File(scalaHome, CompilerModuleName + ".jar")
		def libraryJar = new File(scalaHome, LibraryModuleName + ".jar")
		override def classpath = Provider.getJars(scalaHome :: Nil)
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

			lazy val components = new ComponentProvider(appHome, lockBoot)
		}
	}
}
object Launcher
{
	def apply(bootDirectory: File, repositories: List[xsbti.Repository]): xsbti.Launcher =
		apply(bootDirectory, IvyOptions(None, Classifiers(Nil, Nil), repositories, BootConfiguration.DefaultChecksums))
	def apply(bootDirectory: File, ivyOptions: IvyOptions): xsbti.Launcher =
		apply(bootDirectory, ivyOptions, GetLocks.find)
	def apply(bootDirectory: File, ivyOptions: IvyOptions, locks: xsbti.GlobalLock): xsbti.Launcher =
		new Launch(bootDirectory, true, ivyOptions) {
			override def globalLock = locks
		}
	def apply(explicit: LaunchConfiguration): xsbti.Launcher =
		new Launch(explicit.boot.directory, explicit.boot.lock, explicit.ivyConfiguration)
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
class ComponentProvider(baseDirectory: File, lockBoot: Boolean) extends xsbti.ComponentProvider
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
	def addToComponent(id: String, files: Array[File]): Boolean =
		Copy(files.toList, componentLocation(id))
	def lockFile = if(lockBoot) ComponentProvider.lockFile(baseDirectory) else null // null for the Java interface
}
object ComponentProvider
{
	def lockFile(baseDirectory: File) =
	{
		baseDirectory.mkdirs()
		new File(baseDirectory, "sbt.components.lock")
	}
}
