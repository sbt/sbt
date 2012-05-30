/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010, 2011  Mark Harrah
 */
package xsbt.boot

import Pre._
import BootConfiguration.{CompilerModuleName, LibraryModuleName}
import java.io.File
import java.net.{URL, URLClassLoader}
import java.util.concurrent.Callable
import scala.collection.immutable.List
import scala.annotation.tailrec

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
		val appProvider: xsbti.AppProvider = launcher.app(app, orNull(scalaVersion)) // takes ~40 ms when no update is required
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
			case r: xsbti.Reboot => launch(run)(new RunConfiguration(Option(r.scalaVersion), r.app, r.baseDirectory, r.arguments.toList))
			case x => throw new BootException("Invalid main result: " + x + (if(x eq null) "" else " (class: " + x.getClass + ")"))
		}
	}
}
final class RunConfiguration(val scalaVersion: Option[String], val app: xsbti.ApplicationID, val workingDirectory: File, val arguments: List[String])

import BootConfiguration.{appDirectoryName, baseDirectoryName, extractScalaVersion, ScalaDirectoryName, TestLoadScalaClasses, ScalaOrg}
class Launch private[xsbt](val bootDirectory: File, val lockBoot: Boolean, val ivyOptions: IvyOptions) extends xsbti.Launcher
{
	import ivyOptions.{checksums => checksumsList, classifiers, repositories}
	bootDirectory.mkdirs
	private val scalaProviders = new Cache[(String, String), String, xsbti.ScalaProvider]((x, y) => getScalaProvider(x._1, x._2, y))
	def getScala(version: String): xsbti.ScalaProvider = getScala(version, "")
	def getScala(version: String, reason: String): xsbti.ScalaProvider = getScala(version, reason, ScalaOrg)
	def getScala(version: String, reason: String, scalaOrg: String) = scalaProviders((scalaOrg, version), reason)
	def app(id: xsbti.ApplicationID, version: String): xsbti.AppProvider = app(id, Option(version))
    def app(id: xsbti.ApplicationID, scalaVersion: Option[String]): xsbti.AppProvider =
      getAppProvider(id, scalaVersion, false)

	val bootLoader = new BootFilteredLoader(getClass.getClassLoader)
	val topLoader = jnaLoader(bootLoader)

	val updateLockFile = if(lockBoot) Some(new File(bootDirectory, "sbt.boot.lock")) else None

	def globalLock: xsbti.GlobalLock = Locks
	def ivyHome = orNull(ivyOptions.ivyHome)
	def ivyRepositories = repositories.toArray
	def isOverrideRepositories: Boolean = ivyOptions.isOverrideRepositories
	def checksums = checksumsList.toArray[String]

	def jnaLoader(parent: ClassLoader): ClassLoader =
	{
		val id = AppID("net.java.dev.jna", "jna", "3.2.3", "", toArray(Nil), false, array())
		val configuration = makeConfiguration(ScalaOrg, None)
		val jnaHome = appDirectory(new File(bootDirectory, baseDirectoryName(ScalaOrg, None)), id)
		val module = appModule(id, None, false, "jna")
		def makeLoader(): ClassLoader = {
			val urls = toURLs(wrapNull(jnaHome.listFiles(JarFilter)))
			val loader = new URLClassLoader(urls, bootLoader)
			checkLoader(loader, module, "com.sun.jna.Function" :: Nil, loader)
		}
		val existingLoader =
			if(jnaHome.exists)
				try Some(makeLoader()) catch { case e: Exception => None }
			else
				None
		existingLoader getOrElse {
			update(module, "")
			makeLoader()
		}
	}
	def checkLoader[T](loader: ClassLoader, module: ModuleDefinition, testClasses: Seq[String], ifValid: T): T =
	{
		val missing = getMissing(loader, testClasses)
		if(missing.isEmpty)
			ifValid
		else
			module.retrieveCorrupt(missing)
	}

	private[this] def makeConfiguration(scalaOrg: String, version: Option[String]): UpdateConfiguration =
		new UpdateConfiguration(bootDirectory, ivyOptions.ivyHome, scalaOrg, version, repositories, checksumsList)

	final def getAppProvider(id: xsbti.ApplicationID, explicitScalaVersion: Option[String], forceAppUpdate: Boolean): xsbti.AppProvider =
		locked(new Callable[xsbti.AppProvider] { def call = getAppProvider0(id, explicitScalaVersion, forceAppUpdate) })

	@tailrec private[this] final def getAppProvider0(id: xsbti.ApplicationID, explicitScalaVersion: Option[String], forceAppUpdate: Boolean): xsbti.AppProvider =
	{
		val app = appModule(id, explicitScalaVersion, true, "app")
		val baseDirs = (base: File) => appBaseDirs(base, id)
		def retrieve() = {
			val sv = update(app, "")
			val scalaVersion = strictOr(explicitScalaVersion, sv)
			new RetrievedModule(true, app, sv, baseDirs(scalaHome(ScalaOrg, scalaVersion)))
		}
		val retrievedApp =
			if(forceAppUpdate)
				retrieve()
			else
				existing(app, ScalaOrg, explicitScalaVersion, baseDirs) getOrElse retrieve()

		val scalaVersion = getOrError(strictOr(explicitScalaVersion, retrievedApp.detectedScalaVersion), "No Scala version specified or detected")
		val scalaProvider = getScala(scalaVersion, "(for " + id.name + ")")

		val (missing, appProvider) = checkedAppProvider(id, retrievedApp, scalaProvider)
		if(missing.isEmpty)
			appProvider
		else if(retrievedApp.fresh)
			app.retrieveCorrupt(missing)
		else
			getAppProvider0(id, explicitScalaVersion, true)
	}
	def scalaHome(scalaOrg: String, scalaVersion: Option[String]): File = new File(bootDirectory, baseDirectoryName(scalaOrg, scalaVersion))
	def appHome(id: xsbti.ApplicationID, scalaVersion: Option[String]): File = appDirectory(scalaHome(ScalaOrg, scalaVersion), id)
	def checkedAppProvider(id: xsbti.ApplicationID, module: RetrievedModule, scalaProvider: xsbti.ScalaProvider): (Iterable[String], xsbti.AppProvider) =
	{
		val p = appProvider(id, module, scalaProvider, appHome(id, Some(scalaProvider.version)))
		val missing = getMissing(p.loader, id.mainClass :: Nil)
		(missing, p)				
	}
	private[this] def locked[T](c: Callable[T]): T = Locks(orNull(updateLockFile), c)
	def getScalaProvider(scalaOrg: String, scalaVersion: String, reason: String): xsbti.ScalaProvider =
		locked(new Callable[xsbti.ScalaProvider] { def call = getScalaProvider0(scalaOrg, scalaVersion, reason) })

	private[this] final def getScalaProvider0(scalaOrg: String, scalaVersion: String, reason: String) =
	{
		val scalaM = scalaModule(scalaOrg, scalaVersion)
		val (scalaHome, lib) = scalaDirs(scalaM, scalaOrg, scalaVersion)
		val baseDirs = lib :: Nil
		def provider(retrieved: RetrievedModule): xsbti.ScalaProvider = {
			val p = scalaProvider(scalaVersion, retrieved, topLoader, lib)
			checkLoader(p.loader, retrieved.definition, TestLoadScalaClasses, p)
		}
		existing(scalaM, scalaOrg, Some(scalaVersion), _ => baseDirs) flatMap { mod =>
			try Some(provider(mod))
			catch { case e: Exception => None }
		} getOrElse {
			val scalaVersion = update(scalaM, reason)
			provider( new RetrievedModule(true, scalaM, scalaVersion, baseDirs) )
		}
	}

	def existing(module: ModuleDefinition, scalaOrg: String, explicitScalaVersion: Option[String], baseDirs: File => List[File]): Option[RetrievedModule] =
	{
		val filter = new java.io.FileFilter {
			val explicitName = explicitScalaVersion.map(sv => baseDirectoryName(scalaOrg, Some(sv)))
			def accept(file: File) = file.isDirectory && explicitName.forall(_ == file.getName)
		}
		val retrieved = wrapNull(bootDirectory.listFiles(filter)) flatMap { scalaDir =>
			val appDir = directory(scalaDir, module.target)
			if(appDir.exists)
				new RetrievedModule(false, module, extractScalaVersion(scalaDir), baseDirs(scalaDir)) :: Nil
			else
				Nil
		}
		retrieved.headOption
	}
	def directory(scalaDir: File, target: UpdateTarget): File = target match {
		case _: UpdateScala => scalaDir
		case ua: UpdateApp => appDirectory(scalaDir, ua.id.toID)
	}
	def appBaseDirs(scalaHome: File, id: xsbti.ApplicationID): List[File] =
	{
		val appHome = appDirectory(scalaHome, id)
		val components = componentProvider(appHome)
		appHome :: id.mainComponents.map(components.componentLocation).toList
	}
	def appDirectory(base: File, id: xsbti.ApplicationID): File =
		new File(base, appDirectoryName(id, File.separator))

	def scalaDirs(module: ModuleDefinition, scalaOrg: String, scalaVersion: String): (File, File) =
	{	
		val scalaHome = new File(bootDirectory, baseDirectoryName(scalaOrg, Some(scalaVersion)))
		val libDirectory = new File(scalaHome, ScalaDirectoryName)
		(scalaHome, libDirectory)
	}

	def appProvider(appID: xsbti.ApplicationID, app: RetrievedModule, scalaProvider0: xsbti.ScalaProvider, appHome: File): xsbti.AppProvider = new xsbti.AppProvider
	{
		val scalaProvider = scalaProvider0
		val id = appID
		def mainClasspath = app.fullClasspath
		lazy val loader = app.createLoader(scalaProvider.loader)
		lazy val mainClass: Class[T] forSome { type T <: xsbti.AppMain } =
		{
			val c = Class.forName(id.mainClass, true, loader)
			c.asSubclass(classOf[xsbti.AppMain])
		}
		def newMain(): xsbti.AppMain = mainClass.newInstance

		lazy val components = componentProvider(appHome)
	}
	def componentProvider(appHome: File) = new ComponentProvider(appHome, lockBoot)

	def scalaProvider(scalaVersion: String, module: RetrievedModule, parentLoader: ClassLoader, scalaLibDir: File): xsbti.ScalaProvider = new xsbti.ScalaProvider
	{
		def launcher = Launch.this
		def version = scalaVersion
		lazy val loader = module.createLoader(parentLoader)

		def compilerJar = new File(scalaLibDir, CompilerModuleName + ".jar")
		def libraryJar = new File(scalaLibDir, LibraryModuleName + ".jar")
		def jars = module.fullClasspath
		
		def app(id: xsbti.ApplicationID) = Launch.this.app(id, Some(scalaVersion))
	}

	def appModule(id: xsbti.ApplicationID, scalaVersion: Option[String], getClassifiers: Boolean, tpe: String): ModuleDefinition = new ModuleDefinition(
		configuration = makeConfiguration(ScalaOrg, scalaVersion),
		target = new UpdateApp(Application(id), if(getClassifiers) Value.get(classifiers.app) else Nil, tpe),
		failLabel = id.name + " " + id.version,
		extraClasspath = id.classpathExtra
	)
	def scalaModule(org: String, version: String): ModuleDefinition = new ModuleDefinition(
		configuration = makeConfiguration(org, Some(version)),
		target = new UpdateScala(Value.get(classifiers.forScala)),
		failLabel = "Scala " + version,
		extraClasspath = array()
	)
	def update(mm: ModuleDefinition, reason: String): Option[String] =
	{
		val result = ( new Update(mm.configuration) )(mm.target, reason)
		if(result.success) result.scalaVersion else mm.retrieveFailed
	}
}
object Launcher
{
	def apply(bootDirectory: File, repositories: List[xsbti.Repository]): xsbti.Launcher =
		apply(bootDirectory, IvyOptions(None, Classifiers(Nil, Nil), repositories, BootConfiguration.DefaultChecksums, false))
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
		launcher.app(config.app.toID, orNull(config.getScalaVersion))
	}
}
class ComponentProvider(baseDirectory: File, lockBoot: Boolean) extends xsbti.ComponentProvider
{
	def componentLocation(id: String): File = new File(baseDirectory, id)
	def component(id: String) = wrapNull(componentLocation(id).listFiles).filter(_.isFile)
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
