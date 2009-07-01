/* sbt -- Simple Build Tool
 * Copyright 2008, 2009  Mark Harrah, David MacIver
 */
package sbt

import java.io.File
import java.net.URLClassLoader
import scala.collection._
import FileUtilities._
import Project._

trait Project extends TaskManager with Dag[Project] with BasicEnvironment
{
	/** The logger for this project definition. */
	final val log: Logger = logImpl
	protected def logImpl: Logger = new BufferedLogger(new ConsoleLogger)
	
	trait ActionOption extends NotNull
	
	/** Basic project information. */
	def info: ProjectInfo
	/** The project name. */
	final def name: String = projectName.value
	/** The project version. */
	final def version: Version = projectVersion.value
	/** The project organization. */
	final def organization: String = projectOrganization.value
	/** True if the project should cater to a quick throwaway project setup.*/
	def scratch = projectScratch.value
	
	final type ManagerType = Project
	final type ManagedTask = Project#Task
	/** The tasks declared on this project. */
	def tasks: Map[String, Task]
	/** The task methods declared on this project */
	def methods: Map[String, MethodTask]
	/** The names of all available tasks that may be called through `act`.  These include
	* the names of the Tasks in `tasks` and those of all dependencies.*/
	def taskNames: Iterable[String] = deepTasks.keys.toList
	/** The names of all available method tasks that may be called through `call`.  These
	* only include the names of the MethodTasks in `methods` and not those of dependencies.*/
	def methodNames: Iterable[String] = methods.keys.toList
	/** A description of all available method tasks in this project, but not of dependencies. */
	def methodList: String = descriptionList(methods)
	/** A description of all available tasks in this project and all dependencies.  If there
	* are different tasks with the same name, only one will be included. */
	def taskList: String = descriptionList(deepTasks)
	
	final def taskName(task: Task) = tasks.find( _._2 eq task ).map(_._1).getOrElse(UnnamedName)
	/** A description of all available tasks in this project and all dependencies and all
	* available method tasks in this project, but not of dependencies.  If there
	* are different tasks or methods with the same name, only one will be included. */
	def taskAndMethodList: String = descriptionList(tasksAndMethods)
	/** The actions and methods declared on this project. */
	final def tasksAndMethods: Map[String, Described] =
		immutable.TreeMap.empty[String, Described] ++ methods ++ tasks
	private def descriptionList(described: Map[String, Described]): String =
	{
		val buffer = new StringBuilder
		for((name, d) <- described)
			buffer.append("\t" + name + d.description.map(x => ": " + x).getOrElse("") + "\n")
		buffer.toString
	}
	/** Combines the method task maps of this project and all dependencies.*/
	private[sbt] def deepMethods: Map[String, Project#MethodTask] = deep(_.methods)
	/** Combines the task maps of this project and all dependencies.*/
	private[sbt] def deepTasks: Map[String, Project#Task] = deep(_.tasks)
	private def deep[T](p: Project => Map[String, T]): Map[String, T] =
	{
		var tasks: immutable.SortedMap[String,T] = new immutable.TreeMap[String, T]
		for(dependentProject <- topologicalSort)
			tasks ++= p(dependentProject).elements
		tasks
	}
	/** A map of names to projects for all subprojects of this project.  These are typically explicitly
	* specified for the project and are different from those specified in the project constructor. The
	* main use within sbt is in ParentProject.*/
	def subProjects: Map[String, Project] = immutable.Map.empty
	/** The name of this project and the names of all subprojects/dependencies, transitively.*/
	def projectNames: Iterable[String] =
	{
		val names = new mutable.HashSet[String]
		names ++= subProjects.keys
		for(dependentProject <- topologicalSort)
			names ++= dependentProject.tasks.keys
		names.toList
	}
	
	def call(name: String, parameters: Array[String]): Option[String] =
	{
		methods.get(name) match
		{
			case Some(method) =>run(method(parameters), name)
			case None => Some("Method '" + name + "' does not exist.")
		}
	}
	private def run(task: Project#Task, taskName: String): Option[String] =
		impl.RunTask(task, taskName, parallelExecution) match
		{
			case Nil => None
			case x => Some(Set(x: _*).mkString("\n"))
		}
		
	/** Executes the task with the given name.  This involves executing the task for all
	* project dependencies (transitive) and then for this project.  Not every dependency
	* must define a task with the given name.  If this project and all dependencies
	* do not define a task with the given name, an error is generated indicating this.*/
	def act(name: String): Option[String] =
	{
		val ordered = topologicalSort
		val definedTasks = ordered.flatMap(_.tasks.get(name).toList)
		def virtualTask(name: String): Task = new Task(None, definedTasks.filter(!_.interactive), false, None)

		if(definedTasks.isEmpty)
			Some("Action '" + name + "' does not exist.")
		else
		{
			tasks.get(name) match
			{
				case None =>
					val virtual = virtualTask(name)
					if(virtual.dependencies.size == definedTasks.size)
						run(virtual, name)
					else
					{
						Some("Cannot run interactive action '" + name +
							"' defined on multiple subprojects (change to the desired project with 'project <name>').")
					}
				case Some(task) => run(task, name)
			}
		}
	}
	
	/** Logs the list of projects at the debug level.*/
	private def showBuildOrder(order: Iterable[Project])
	{
		log.debug("Project build order:")
		order.foreach(x => log.debug("    " + x.name) )
		log.debug("")
	}
	
	/** Converts a String to a path relative to the project directory of this project. */
	implicit def path(component: String): Path = info.projectPath / component
	/** Converts a String to a simple name filter.  * has the special meaning: zero or more of any character */
	implicit def filter(simplePattern: String): NameFilter = GlobFilter(simplePattern)
	
	/** Loads the project at the given path and declares the project to have the given
	* dependencies.  This method will configure the project according to the
	* project/ directory in the directory denoted by path.*/
	def project(path: Path, deps: Project*): Project = getProject(Project.loadProject(path, deps, Some(this), log), path)
	
	/** Loads the project at the given path using the given name and inheriting this project's version.
	* The builder class is the default builder class, sbt.DefaultProject. The loaded project is declared
	* to have the given dependencies. Any project/build/ directory for the project is ignored.*/
	def project(path: Path, name: String, deps: Project*): Project = project(path, name, Project.DefaultBuilderClass, deps: _*)
	
	/** Loads the project at the given path using the given name and inheriting it's version from this project.
	* The Project implementation used is given by builderClass.  The dependencies are declared to be
	* deps. Any project/build/ directory for the project is ignored.*/
	def project[P <: Project](path: Path, name: String, builderClass: Class[P], deps: Project*): P =
	{
		require(builderClass != this.getClass, "Cannot recursively construct projects of same type: " + builderClass.getName)
		project(path, name, info => Project.constructProject(info, builderClass), deps: _*)
	}
	/** Loads the project at the given path using the given name and inheriting it's version from this project.
	* The construct function is used to obtain the Project instance. Any project/build/ directory for the project
	* is ignored.  The project is declared to have the dependencies given by deps.*/
	def project[P <: Project](path: Path, name: String, construct: ProjectInfo => P, deps: Project*): P =
		initialize(construct(ProjectInfo(path.asFile, deps, Some(this))), Some(new SetupInfo(name, None, None, false)), log)
	
	/** Initializes the project directories when a user has requested that sbt create a new project.*/
	def initializeDirectories() {}
	/** True if projects should be run in parallel, false if they should run sequentially.
	*  This only has an effect for multi-projects.  If this project has a parent, this value is
	* inherited from that parent project.*/
	def parallelExecution: Boolean =
		info.parent match
		{
			case Some(parent) => parent.parallelExecution
			case None => false
		}
	
	/** True if a project and its dependencies should be checked to ensure that their
	* output directories are not the same, false if they should not be checked. */
	def shouldCheckOutputDirectories = true
	
	/** The list of directories to which this project writes.  This is used to verify that multiple
	* projects have not been defined with the same output directories. */
	def outputDirectories: Iterable[Path] = outputRootPath :: Nil
	def rootProject = Project.rootProject(this)
	/** The path to the file that provides persistence for properties.*/
	final def envBackingPath = info.builderPath / Project.DefaultEnvBackingName
	/** The path to the file that provides persistence for history. */
	def historyPath: Option[Path] = Some(outputRootPath / ".history")
	def outputPath = crossPath(outputRootPath)
	def outputRootPath = outputDirectoryName
	def outputDirectoryName = DefaultOutputDirectoryName
	
	private def getProject(result: LoadResult, path: Path): Project =
		result match
		{
			case LoadSetupDeclined => Predef.error("No project exists at path " + path)
			case lse: LoadSetupError => Predef.error("Error setting up new project at path " + Path + " : " + lse.message)
			case err: LoadError => Predef.error("Error loading project at path " + path + " : " + err.message)
			case success: LoadSuccess => success.project
		}
	
	/** The property for the project's version. */
	final val projectVersion = property[Version]
	/** The property for the project's name. */
	final val projectName = propertyLocalF[String](NonEmptyStringFormat)
	/** The property for the project's organization.  Defaults to the parent project's organization or the project name if there is no parent. */
	final val projectOrganization = propertyOptional[String](normalizedName, true)
	/** The property that defines the version of Scala to build this project with by default.  This property is only
	* ready by `sbt` on startup and reboot.  When cross-building, this value may be different from the actual
	* version of Scala being used to build the project.  ScalaVersion.current and ScalaVersion.cross should be used
	* to read the version of Scala building the project.  This should only be used to change the version of Scala used
	* for normal development (not cross-building)*/
	final val scalaVersion = propertyOptional[String]("")
	final val sbtVersion = propertyOptional[String]("")
	final val projectInitialize = propertyOptional[Boolean](false)
	final val projectScratch = propertyOptional[Boolean](false)

	/** If this project is cross-building, returns `base` with an additional path component containing the scala version.
	* Otherwise, this returns `base`.
	* By default, cross-building is enabled when a project is loaded by the loader and crossScalaVersions is not empty.*/
	def crossPath(base: Path) = ScalaVersion.withCross(disableCrossPaths)(base / ScalaVersion.crossString(_), base)
	/** If modifying paths for cross-building is enabled, this returns ScalaVersion.currentString.
	* Otherwise, this returns the empty string. */
	def crossScalaVersionString: String = if(disableCrossPaths) "" else ScalaVersion.currentString
	
	/** True if crossPath should be the identity function.*/
	protected def disableCrossPaths = crossScalaVersions.isEmpty
	/** By default, this is empty and cross-building is disabled.  Overriding this to a Set of Scala versions
	* will enable cross-building against those versions.*/
	def crossScalaVersions = scala.collection.immutable.Set.empty[String]
	/** A `PathFinder` that determines the files watched when an action is run with a preceeding ~ when this is the current
	* project.  This project does not need to include the watched paths for projects that this project depends on.*/
	def watchPaths: PathFinder = Path.emptyPathFinder
	
	protected final override def parentEnvironment = info.parent
	
	// .* included because svn doesn't mark .svn hidden
	def defaultExcludes: FileFilter = (".*"  - ".") || HiddenFileFilter
	/** Short for parent.descendentsExcept(include, defaultExcludes)*/
	def descendents(parent: PathFinder, include: FileFilter) = parent.descendentsExcept(include, defaultExcludes)
	override def toString = "Project " + projectName.get.getOrElse("at " + environmentLabel)
	
	def normalizedName = StringUtilities.normalize(name)
}
private[sbt] sealed trait LoadResult extends NotNull
private[sbt] final class LoadSuccess(val project: Project) extends LoadResult
private[sbt] final class LoadError(val message: String) extends LoadResult
private[sbt] final object LoadSetupDeclined extends LoadResult
private[sbt] final class LoadSetupError(val message: String) extends LoadResult

object Project
{
	val UnnamedName = "<anonymous>"
	val BootDirectoryName = "boot"
	val DefaultOutputDirectoryName = "target"
	val DefaultEnvBackingName = "build.properties"
	val DefaultBuilderClassName = "sbt.DefaultProject"
	val DefaultBuilderClass = Class.forName(DefaultBuilderClassName).asSubclass(classOf[Project])
	
	/** The name of the directory for project definitions.*/
	val BuilderProjectDirectoryName = "build"
	/** The name of the directory for plugin definitions.*/
	val PluginProjectDirectoryName = "plugins"
	/** The name of the class that all projects must inherit from.*/
	val ProjectClassName = classOf[Project].getName
	
	/** The logger that should be used before the root project definition is loaded.*/
	private[sbt] def bootLogger =
	{
		val log = new ConsoleLogger
		log.setLevel(Level.Debug)
		log.enableTrace(true)
		log
	}

	private[sbt] def booted = java.lang.Boolean.getBoolean("sbt.boot")
	
	/** Loads the project in the current working directory.*/
	private[sbt] def loadProject: LoadResult = loadProject(bootLogger)
	/** Loads the project in the current working directory.*/
	private[sbt] def loadProject(log: Logger): LoadResult = checkOutputDirectories(loadProject(new File("."), Nil, None, log))
	/** Loads the project in the directory given by 'path' and with the given dependencies.*/
	private[sbt] def loadProject(path: Path, deps: Iterable[Project], parent: Option[Project], log: Logger): LoadResult =
		loadProject(path.asFile, deps, parent, log)
	/** Loads the project in the directory given by 'projectDirectory' and with the given dependencies.*/
	private[sbt] def loadProject(projectDirectory: File, deps: Iterable[Project], parent: Option[Project], log: Logger): LoadResult =
		loadProject(projectDirectory, deps, parent, getClass.getClassLoader, log)
	private[sbt] def loadProject(projectDirectory: File, deps: Iterable[Project], parent: Option[Project], additional: ClassLoader, log: Logger): LoadResult =
	{
		val info = ProjectInfo(projectDirectory, deps, parent)
		ProjectInfo.setup(info, log) match
		{
			case err: SetupError => new LoadSetupError(err.message)
			case SetupDeclined => LoadSetupDeclined
			case AlreadySetup => loadProject(info, None, additional, log)
			case setup: SetupInfo => loadProject(info, Some(setup), additional, log)
		}
	}
	private def loadProject(info: ProjectInfo, setupInfo: Option[SetupInfo], additional: ClassLoader, log: Logger): LoadResult =
	{
		try
		{
			val oldLevel = log.getLevel
			log.setLevel(Level.Warn)
			val result =
				for(builderClass <- getProjectDefinition(info, additional, log).right) yield
					initialize(constructProject(info, builderClass), setupInfo, log)
			log.setLevel(oldLevel)
			result.fold(new LoadError(_), new LoadSuccess(_))
		}
		catch
		{
			case ite: java.lang.reflect.InvocationTargetException =>
			{
				val cause =
					if(ite.getCause == null) ite
					else ite.getCause
				errorLoadingProject(cause, log)
			}
			case nme: NoSuchMethodException => new LoadError("Constructor with one argument of type sbt.ProjectInfo required for project definition.")
			case e: Exception => errorLoadingProject(e, log)
		}
	}
	/** Logs the stack trace and returns an error message in Left.*/
	private def errorLoadingProject(e: Throwable, log: Logger) =
	{
		log.trace(e)
		new LoadError("Error loading project: " + e.toString)
	}
	/** Loads the project for the given `info` and represented by an instance of 'builderClass'.*/
	private[sbt] def constructProject[P <: Project](info: ProjectInfo, builderClass: Class[P]): P =
		builderClass.getConstructor(classOf[ProjectInfo]).newInstance(info)
	/** Checks the project's dependencies, initializes its environment, and possibly its directories.*/
	private def initialize[P <: Project](p: P, setupInfo: Option[SetupInfo], log: Logger): P =
	{
		setupInfo match
		{
			case Some(setup) =>
			{
				p.projectName() = setup.name
				for(v <- setup.version)
					p.projectVersion() = v
				for(org <- setup.organization)
					p.projectOrganization() = org
				if(!setup.initializeDirectories)
					p.setEnvironmentModified(false)
				for(errorMessage <- p.saveEnvironment())
					log.error(errorMessage)
				if(setup.initializeDirectories)
					p.initializeDirectories()
			}
			case None =>
				if(p.projectInitialize.value)
				{
					p.initializeDirectories()
					p.projectInitialize() = false
					for(errorMessage <- p.saveEnvironment())
						log.error(errorMessage)
				}
		}
		val useName = p.projectName.get.getOrElse("at " + p.info.projectDirectory.getAbsolutePath)
		checkDependencies(useName, p.info.dependencies, log)
		p
	}
	/** Compiles the project definition classes and returns the project definition class name
	* and the class loader that should be used to load the definition. */
	private def getProjectDefinition(info: ProjectInfo, additional: ClassLoader, buildLog: Logger): Either[String, Class[P] forSome { type P <: Project }] =
	{
		val builderProjectPath = info.builderPath / BuilderProjectDirectoryName
		if(builderProjectPath.asFile.isDirectory)
		{
			val pluginProjectPath = info.builderPath / PluginProjectDirectoryName
			val additionalPaths = additional match { case u: URLClassLoader => u.getURLs.map(url => Path.fromFile(new File(url.toURI))); case _ => Nil }
			val builderProject = new BuilderProject(ProjectInfo(builderProjectPath.asFile, Nil, None), pluginProjectPath, additionalPaths, buildLog)
			builderProject.compile.run.toLeft(()).right.flatMap { ignore =>
				builderProject.projectDefinition.right.map {
					case Some(definition) => getProjectClass[Project](definition, builderProject.projectClasspath, additional)
					case None => DefaultBuilderClass
				}
			}
		}
		else
			Right(DefaultBuilderClass)
	}
	/** Verifies that the given list of project dependencies contains no nulls.  The
	* String argument should be the project name with the dependencies.*/
	private def checkDependencies(forProject: String, deps: Iterable[Project], log: Logger)
	{
		for(nullDep <- deps.find(_ == null))
		{
			log.error("Project " + forProject + " had a null dependency.  This is probably an initialization problem and might be due to a circular dependency.")
			throw new RuntimeException("Null dependency in project " + forProject)
		}
	}
	/** Verifies that output directories of the given project and all of its dependencies are
	* all different.  No verification is done if the project overrides
	* 'shouldCheckOutputDirectories' to be false. The 'Project.outputDirectories' method is
	* used to determine a project's output directories. */
	private def checkOutputDirectories(result: LoadResult): LoadResult =
		result match
		{
			case success: LoadSuccess =>
				if(success.project.shouldCheckOutputDirectories)
					checkOutputDirectoriesImpl(success.project)
				else
					success
			case x => x
		}
	/** Verifies that output directories of the given project and all of its dependencies are
	* all different.  The 'Project.outputDirectories' method is used to determine a project's
	* output directories. */
	private def checkOutputDirectoriesImpl(project: Project): LoadResult =
	{
		val projects = project.topologicalSort
		import scala.collection.mutable.{HashMap, HashSet, Set}
		val outputDirectories = new HashMap[Path, Set[Project]]
		for(p <- projects; path <- p.outputDirectories)
			outputDirectories.getOrElseUpdate(path, new HashSet[Project]) += p
		val shared = outputDirectories.filter(_._2.size > 1)
		if(shared.isEmpty)
			new LoadSuccess(project)
		else
		{
			val sharedString =
			{
				val s =
					for((path, projectsSharingPath) <- shared) yield
						projectsSharingPath.map(_.name).mkString(", ") + " share " + path
				s.mkString("\n\t")
			}
			new LoadError("The same directory is used for output for multiple projects:\n\t" + sharedString +
			"\n  (If this is intentional, use 'override def shouldCheckOutputDirectories = false' in your project definition.)")
		}
	}
	import scala.reflect.Manifest
	private[sbt] def getProjectClass[P <: Project](name: String, classpath: PathFinder, additional: ClassLoader)(implicit mf: Manifest[P]): Class[P] =
	{
		val loader =ClasspathUtilities.toLoader(classpath, additional)
		val builderClass = Class.forName(name, false, loader)
		val projectClass = mf.erasure
		require(projectClass.isAssignableFrom(builderClass), "Builder class '" + builderClass + "' does not extend " + projectClass.getName + ".")
		builderClass.asSubclass(projectClass).asInstanceOf[Class[P]]
	}
	
	/** Writes the project name and a separator to the project's log at the info level.*/
	def showProjectHeader(project: Project)
	{
		val projectHeader = "Project " + project.name
		project.log.info("")
		project.log.info(projectHeader)
		project.log.info("=" * projectHeader.length)
	}
	
	def rootProject(p: Project): Project =
		p.info.parent match
		{
			case Some(parent) => rootProject(parent)
			case None => p
		}
}
