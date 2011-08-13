/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URL
	import Project.ScopedKey
	import complete._
	import inc.Analysis
	import inc.Locate.DefinesClass
	import std.TaskExtra._
	import scala.xml.{Node => XNode, NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import org.scalatools.testing.Framework
	import Configurations.CompilerPlugin

object Keys
{
	val TraceValues = "-1 to disable, 0 for up to the first sbt frame, or a positive number to set the maximum number of frames shown."

	// logging
	val logLevel = SettingKey[Level.Value]("log-level", "The amount of logging sent to the screen.")
	val persistLogLevel = SettingKey[Level.Value]("persist-log-level", "The amount of logging sent to a file for persistence.")
	val traceLevel = SettingKey[Int]("trace-level", "The amount of a stack trace displayed.  " + TraceValues)
	val persistTraceLevel = SettingKey[Int]("persist-trace-level", "The amount of stack trace persisted.")
	val showSuccess = SettingKey[Boolean]("show-success", "If true, displays a success message after running a command successfully.")
	val showTiming = SettingKey[Boolean]("show-timing", "If true, the command success message includes the completion time.")
	val timingFormat = SettingKey[java.text.DateFormat]("timing-format", "The format used for displaying the completion time.")
	val extraLoggers = SettingKey[ScopedKey[_] => Seq[AbstractLogger]]("extra-loggers", "A function that provides additional loggers for a given setting.")
	val logManager = SettingKey[LogManager]("log-manager", "The log manager, which creates Loggers for different contexts.")
	val logBuffered = SettingKey[Boolean]("log-buffered", "True if logging should be buffered until work completes.")

	// Project keys
	val projectCommand = AttributeKey[Boolean]("project-command", "Marks Commands that were registered for the current Project.")
	val sessionSettings = AttributeKey[SessionSettings]("session-settings", "Tracks current build, project, and setting modifications.")
	val stateBuildStructure = AttributeKey[Load.BuildStructure]("build-structure", "Data structure containing all information about the build definition.")
	val buildStructure = TaskKey[Load.BuildStructure]("build-structure", "Provides access to the build structure, settings, and streams manager.")
	val appConfiguration = SettingKey[xsbti.AppConfiguration]("app-configuration", "Provides access to the launched sbt configuration, including the ScalaProvider, Launcher, and GlobalLock.")
	val thisProject = SettingKey[ResolvedProject]("this-project", "Provides the current project for the referencing scope.")
	val thisProjectRef = SettingKey[ProjectRef]("this-project-ref", "Provides a fully-resolved reference to the current project for the referencing scope.")
	val configuration = SettingKey[Configuration]("configuration", "Provides the current configuration of the referencing scope.")
	val commands = SettingKey[Seq[Command]]("commands", "Defines commands to be registered when this project or build is the current selected one.")
	val initialize = SettingKey[Unit]("initialize", "A convenience setting for performing side-effects during initialization.")
	val onLoad = SettingKey[State => State]("on-load", "Transformation to apply to the build state when the build is loaded.")
	val onUnload = SettingKey[State => State]("on-unload", "Transformation to apply to the build state when the build is unloaded.")

	val onComplete = SettingKey[() => Unit]("on-complete", "Hook to run when task evaluation completes.  The type of this setting is subject to change, pending the resolution of SI-2915.")
// https://issues.scala-lang.org/browse/SI-2915
//	val onComplete = SettingKey[RMap[Task,Result] => RMap[Task,Result]]("on-complete", "Transformation to apply to the final task result map.  This may also be used to register hooks to run when task evaluation completes.")

	// Command keys
	val globalLogging = SettingKey[GlobalLogging]("global-logging", "Provides a global Logger, including command logging.")
	val historyPath = SettingKey[Option[File]]("history", "The location where command line history is persisted.")
	val shellPrompt = SettingKey[State => String]("shell-prompt", "The function that constructs the command prompt from the current build state.")
	val analysis = AttributeKey[inc.Analysis]("analysis", "Analysis of compilation, including dependencies and generated outputs.")
	val watch = SettingKey[Watched]("watch", "Continuous execution configuration.")
	val pollInterval = SettingKey[Int]("poll-interval", "Interval between checks for modified sources by the continuous execution command.")
	val watchSources = TaskKey[Seq[File]]("watch-sources", "Defines the sources in this project for continuous execution to watch for changes.")
	val watchTransitiveSources = TaskKey[Seq[File]]("watch-transitive-sources", "Defines the sources in all projects for continuous execution to watch.")

	// Path Keys
	val baseDirectory = SettingKey[File]("base-directory", "The base directory.  Depending on the scope, this is the base directory for the build, project, configuration, or task.")
	val globalBaseDirectory = AttributeKey[File]("global-base-directory", "The base directory for global sbt configuration and staging.")
	val target = SettingKey[File]("target", "Main directory for files generated by the build.")
	val crossTarget = SettingKey[File]("cross-target", "Main directory for files generated by the build that are cross-built.")

		// Source paths
	val sourceDirectory = SettingKey[File]("source-directory", "Default directory containing sources.")
	val sourceManaged = SettingKey[File]("source-managed", "Default directory for sources generated by the build.")
	val scalaSource = SettingKey[File]("scala-source", "Default Scala source directory.")
	val javaSource = SettingKey[File]("java-source", "Default Java source directory.")
	val sourceDirectories = SettingKey[Seq[File]]("source-directories", "List of all source directories, both managed and unmanaged.")
	val unmanagedSourceDirectories = SettingKey[Seq[File]]("unmanaged-source-directories", "Unmanaged source directories, which contain manually created sources.")
	val unmanagedSources = TaskKey[Seq[File]]("unmanaged-sources", "Unmanaged sources, which are manually created.")
	val managedSourceDirectories = SettingKey[Seq[File]]("managed-source-directories", "Managed source directories, which contain sources generated by the build.")
	val managedSources = TaskKey[Seq[File]]("managed-sources", "Sources generated by the build.")
	val sources = TaskKey[Seq[File]]("sources", "All sources, both managed and unmanaged.")

		// Filters
	val sourceFilter = SettingKey[FileFilter]("source-filter", "Filter for selecting sources from default directories.")
	val defaultExcludes = SettingKey[FileFilter]("default-excludes", "Filter for excluding files, such as sources and resources, by default.")

		// Resource paths
	val resourceDirectory = SettingKey[File]("resource-directory", "Default unmanaged resource directory, used for user-defined resources.")
	val resourceManaged = SettingKey[File]("resource-managed", "Default managed resource directory, used when generating resources.")
	val unmanagedResourceDirectories = SettingKey[Seq[File]]("unmanaged-resource-directories", "Unmanaged resource directories, containing resources manually created by the user.")
	val unmanagedResources = TaskKey[Seq[File]]("unmanaged-resources", "Unmanaged resources, which are manually created.")
	val managedResourceDirectories = SettingKey[Seq[File]]("managed-resource-directories", "List of managed resource directories.")
	val managedResources = TaskKey[Seq[File]]("managed-resources", "Resources generated by the build.")
	val resourceDirectories = SettingKey[Seq[File]]("resource-directories", "List of all resource directories, both managed and unmanaged.")
	val resources = TaskKey[Seq[File]]("resources", "All resource files, both managed and unmanaged.")

		// Output paths
	val classDirectory = SettingKey[File]("class-directory", "Directory for compiled classes and copied resources.")
	val docDirectory = SettingKey[File]("doc-directory", "Directory for generated documentation.")
	val cacheDirectory = SettingKey[File]("cache-directory", "Directory used for caching task data.")
	val cleanFiles = SettingKey[Seq[File]]("clean-files", "The files to recursively delete during a clean.")
	val cleanKeepFiles = SettingKey[Seq[File]]("clean-keep-files", "Files to keep during a clean.")
	val crossPaths = SettingKey[Boolean]("cross-paths", "If true, enables cross paths, which distinguish output directories for cross-building.")
	val taskTemporaryDirectory = SettingKey[File]("task-temporary-directory", "Directory used for temporary files for tasks that is deleted after each task execution.")

		// Generators
	val sourceGenerators = SettingKey[Seq[Task[Seq[File]]]]("source-generators", "List of tasks that generate sources.")
	val resourceGenerators = SettingKey[Seq[Task[Seq[File]]]]("resource-generators", "List of tasks that generate resources.")

	// compile/doc keys
	val autoCompilerPlugins = SettingKey[Boolean]("auto-compiler-plugins", "If true, enables automatically generating -Xplugin arguments to the compiler based on the classpath for the " + CompilerPlugin.name + " configuration.")
	val maxErrors = SettingKey[Int]("max-errors", "The maximum number of errors, such as compile errors, to list.")
	val scaladocOptions = TaskKey[Seq[String]]("scaladoc-options", "Options for Scaladoc.")
	val scalacOptions = TaskKey[Seq[String]]("scalac-options", "Options for the Scala compiler.")
	val javacOptions = SettingKey[Seq[String]]("javac-options", "Options for the Java compiler.")
	val compileOrder = SettingKey[CompileOrder.Value]("compile-order", "Configures the order in which Java and sources within a single compilation are compiled.  Valid values are: JavaThenScala, ScalaThenJava, or Mixed.")
	val initialCommands = SettingKey[String]("initial-commands", "Initial commands to execute when starting up the Scala interpreter.")
	val compileInputs = TaskKey[Compiler.Inputs]("compile-inputs", "Collects all inputs needed for compilation.")
	val scalaHome = SettingKey[Option[File]]("scala-home", "If Some, defines the local Scala installation to use for compilation, running, and testing.")
	val scalaInstance = TaskKey[ScalaInstance]("scala-instance", "Defines the Scala instance to use for compilation, running, and testing.")
	val scalaVersion = SettingKey[String]("scala-version", "The version of Scala used for building.")
	val crossScalaVersions = SettingKey[Seq[String]]("cross-scala-versions", "The versions of Scala used when cross-building.")
	val classpathOptions = SettingKey[ClasspathOptions]("classpath-options", "Configures handling of Scala classpaths.")
	val definedSbtPlugins = TaskKey[Set[String]]("defined-sbt-plugins", "The set of names of Plugin implementations defined by this project.")
	val sbtPlugin = SettingKey[Boolean]("sbt-plugin", "If true, enables adding sbt as a dependency and auto-generation of the plugin descriptor file.")

	val clean = TaskKey[Unit]("clean", "Deletes files produced by the build, such as generated sources, compiled classes, and task caches.")
	val console = TaskKey[Unit]("console", "Starts the Scala interpreter with the project classes on the classpath.")
	val consoleQuick = TaskKey[Unit]("console-quick", "Starts the Scala interpreter with the project dependencies on the classpath.", console)
	val consoleProject = TaskKey[Unit]("console-project", "Starts the Scala interpreter with the sbt and the build definition on the classpath and useful imports.")
	val compile = TaskKey[Analysis]("compile", "Compiles sources.")
	val compilers = TaskKey[Compiler.Compilers]("compilers", "Defines the Scala and Java compilers to use for compilation.")
	val compileIncSetup = TaskKey[Compiler.IncSetup]("inc-compile-setup", "Configurations aspects of incremental compilation.")
	val definesClass = TaskKey[DefinesClass]("defines-class", "Internal use: provides a function that determines whether the provided file contains a given class.")
	val doc = TaskKey[File]("doc", "Generates API documentation.")
	val copyResources = TaskKey[Seq[(File,File)]]("copy-resources", "Copies resources to the output directory.")
	val aggregate = SettingKey[Aggregation]("aggregate", "Configures task aggregation.")

	// package keys
	val packageBin = TaskKey[File]("package", "Produces the main artifact, such as a binary jar.")
	val packageDoc = TaskKey[File]("package-doc", "Produces a documentation artifact, such as a jar containing API documentation.")
	val packageSrc = TaskKey[File]("package-src", "Produces a source artifact, such as a jar containing sources and resources.")

	val packageOptions = TaskKey[Seq[PackageOption]]("package-options", "Options for packaging.")
	val packageConfiguration = TaskKey[Package.Configuration]("package-configuration", "Collects all inputs needed for packaging.")
	val artifactPath = SettingKey[File]("artifact-path", "The location of a generated artifact.")
	val artifact = SettingKey[Artifact]("artifact", "Describes an artifact.")
	val artifactClassifier = SettingKey[Option[String]]("artifact-classifier", "Sets the classifier used by the default artifact definition.")
	val artifactName = SettingKey[(String, ModuleID, Artifact) => String]("artifact-name", "Function that produces the artifact name from its definition.")
	val mappings = TaskKey[Seq[(File,String)]]("mappings", "Defines the mappings from a file to a path, used by packaging, for example.")
	val fileMappings = TaskKey[Seq[(File,File)]]("file-mappings", "Defines the mappings from a file to a file, used for copying files, for example.")

	// Run Keys
	val selectMainClass = TaskKey[Option[String]]("select-main-class", "Selects the main class to run.")
	val mainClass = TaskKey[Option[String]]("main-class", "Defines the main class for packaging or running.")
	val run = InputKey[Unit]("run", "Runs a main class, passing along arguments provided on the command line.")
	val runMain = InputKey[Unit]("run-main", "Runs the main class selected by the first argument, passing the remaining arguments to the main method.")
	val discoveredMainClasses = TaskKey[Seq[String]]("discovered-main-classes", "Auto-detects main classes.")
	val runner = TaskKey[ScalaRun]("runner", "Implementation used to run a main class.")
	val trapExit = SettingKey[Boolean]("trap-exit", "If true, enables exit trapping and thread management for 'run'-like tasks.  This is currently only suitable for serially-executed 'run'-like tasks.")

	val fork = SettingKey[Boolean]("fork", "If true, forks a new JVM when running.  If false, runs in the same JVM as the build.")
	val outputStrategy = SettingKey[Option[sbt.OutputStrategy]]("output-strategy", "Selects how to log output when running a main class.")
	val javaHome = SettingKey[Option[File]]("java-home", "Selects the Java installation used for compiling and forking.  If None, uses the Java installation running the build.")
	val javaOptions = SettingKey[Seq[String]]("java-options", "Options passed to a new JVM when forking.")

	// Test Keys
	val testLoader = TaskKey[ClassLoader]("test-loader", "Provides the class loader used for testing.")
	val loadedTestFrameworks = TaskKey[Map[TestFramework,Framework]]("loaded-test-frameworks", "Loads Framework definitions from the test loader.")
	val definedTests = TaskKey[Seq[TestDefinition]]("defined-tests", "Provides the list of defined tests.")
	val executeTests = TaskKey[Tests.Output]("execute-tests", "Executes all tests, producing a report.")
	val test = TaskKey[Unit]("test", "Executes all tests.")
	val testOnly = InputKey[Unit]("test-only", "Executes the tests provided as arguments or all tests if no arguments are provided.")
	val testOptions = TaskKey[Seq[TestOption]]("test-options", "Options for running tests.")
	val testFrameworks = SettingKey[Seq[TestFramework]]("test-frameworks", "Registered, although not necessarily present, test frameworks.")
	val testListeners = TaskKey[Seq[TestReportListener]]("test-listeners", "Defines test listeners.")
	val isModule = AttributeKey[Boolean]("is-module", "True if the target is a module.")
		
	// Classpath/Dependency Management Keys
	type Classpath = Seq[Attributed[File]]
	
	val name = SettingKey[String]("name", "Project name.")
	val normalizedName = SettingKey[String]("normalized-name", "Project name transformed from mixed case and spaces to lowercase and dash-separated.")
	val description = SettingKey[String]("description", "Project description.")
	val homepage = SettingKey[Option[URL]]("homepage", "Project homepage.")
	val licenses = SettingKey[Seq[(String, URL)]]("licenses", "Project licenses as (name, url) pairs.")
	val organization = SettingKey[String]("organization", "Organization/group ID.")
	val organizationName = SettingKey[String]("organization-name", "Organization full/formal name.")
	val organizationHomepage = SettingKey[Option[URL]]("organization-homepage", "Organization homepage.")
	val projectInfo = SettingKey[ModuleInfo]("project-info", "Addition project information like formal name, homepage, licenses etc.")
	val defaultConfiguration = SettingKey[Option[Configuration]]("default-configuration", "Defines the configuration used when none is specified for a dependency.")
	val defaultConfigurationMapping = SettingKey[String]("default-configuration-mapping", "Defines the mapping used for a simple, unmapped configuration definition.")

	val products = TaskKey[Seq[File]]("products", "Build products that get packaged.")
	val productDirectories = TaskKey[Seq[File]]("product-directories", "Base directories of build products.")
	val exportJars = SettingKey[Boolean]("export-jars", "Determines whether the exported classpath for this project contains classes (false) or a packaged jar (true).")
	val exportedProducts = TaskKey[Classpath]("exported-products", "Build products that go on the exported classpath.")
	val unmanagedClasspath = TaskKey[Classpath]("unmanaged-classpath", "Classpath entries (deep) that are manually managed.")
	val unmanagedJars = TaskKey[Classpath]("unmanaged-jars", "Classpath entries for the current project (shallow) that are manually managed.")
	val managedClasspath = TaskKey[Classpath]("managed-classpath", "The classpath consisting of external, managed library dependencies.")
	val internalDependencyClasspath = TaskKey[Classpath]("internal-dependency-classpath", "The internal (inter-project) classpath.")
	val externalDependencyClasspath = TaskKey[Classpath]("external-dependency-classpath", "The classpath consisting of library dependencies, both managed and unmanaged.")
	val dependencyClasspath = TaskKey[Classpath]("dependency-classpath", "The classpath consisting of internal and external, managed and unmanaged dependencies.")
	val fullClasspath = TaskKey[Classpath]("full-classpath", "The exported classpath, consisting of build products and unmanaged and managed, internal and external dependencies.")
	
	val internalConfigurationMap = SettingKey[Configuration => Configuration]("internal-configuration-map", "Maps configurations to the actual configuration used to define the classpath.")
	val classpathConfiguration = SettingKey[Configuration]("classpath-configuration", "The configuration used to define the classpath.")
	val ivyConfiguration = TaskKey[IvyConfiguration]("ivy-configuration", "General dependency management (Ivy) settings, such as the resolvers and paths to use.")
	val ivyConfigurations = SettingKey[Seq[Configuration]]("ivy-configurations", "The defined configurations for dependency management.  This may be different from the configurations for Project settings.")
	val moduleSettings = TaskKey[ModuleSettings]("module-settings", "Module settings, which configure a specific module, such as a project.")
	val unmanagedBase = SettingKey[File]("unmanaged-base", "The default directory for manually managed libraries.")
	val updateConfiguration = SettingKey[UpdateConfiguration]("update-configuration", "Configuration for resolving and retrieving managed dependencies.")
	val ivySbt = TaskKey[IvySbt]("ivy-sbt", "Provides the sbt interface to Ivy.")
	val ivyModule = TaskKey[IvySbt#Module]("ivy-module", "Provides the sbt interface to a configured Ivy module.")
	val classpathFilter = SettingKey[FileFilter]("classpath-filter", "Filter for selecting unmanaged dependencies.")
	val update = TaskKey[UpdateReport]("update", "Resolves and optionally retrieves dependencies, producing a report.")
	val updateClassifiers = TaskKey[UpdateReport]("update-classifiers", "Resolves and optionally retrieves classified artifacts, such as javadocs and sources, for dependency definitions, transitively.", update)
	val transitiveClassifiers = SettingKey[Seq[String]]("transitive-classifiers", "List of classifiers used for transitively obtaining extra artifacts for sbt or declared dependencies.")
	val updateSbtClassifiers = TaskKey[UpdateReport]("update-sbt-classifiers", "Resolves and optionally retrieves classifiers, such as javadocs and sources, for sbt, transitively.", updateClassifiers)
	
	val publishConfiguration = TaskKey[PublishConfiguration]("publish-configuration", "Configuration for publishing to a repository.")
	val publishLocalConfiguration = TaskKey[PublishConfiguration]("publish-local-configuration", "Configuration for publishing to the local repository.")
	val deliverConfiguration = TaskKey[DeliverConfiguration]("deliver-configuration", "Configuration for generating the finished Ivy file for publishing.")
	val deliverLocalConfiguration = TaskKey[DeliverConfiguration]("deliver-local-configuration", "Configuration for generating the finished Ivy file for local publishing.")
	val makePomConfiguration = SettingKey[MakePomConfiguration]("make-pom-configuration", "Configuration for generating a pom.")
	val packagedArtifacts = TaskKey[Map[Artifact,File]]("packaged-artifacts", "Packages all artifacts for publishing and maps the Artifact definition to the generated file.")
	val publishMavenStyle = SettingKey[Boolean]("publish-maven-style", "Configures whether to generate and publish a pom (true) or Ivy file (false).")
	val credentials = TaskKey[Seq[Credentials]]("credentials", "The credentials to use for updating and publishing.")

	val makePom = TaskKey[File]("make-pom", "Generates a pom for publishing when publishing Maven-style.")
	val deliver = TaskKey[File]("deliver", "Generates the Ivy file for publishing to a repository.")
	val deliverLocal = TaskKey[File]("deliver-local", "Generates the Ivy file for publishing to the local repository.")
	val publish = TaskKey[Unit]("publish", "Publishes artifacts to a repository.")
	val publishLocal = TaskKey[Unit]("publish-local", "Publishes artifacts to the local repository.")

	val pomExtra = SettingKey[NodeSeq]("pom-extra", "Extra XML to insert into the generated POM.")
	val pomPostProcess = SettingKey[XNode => XNode]("pom-post-process", "Transforms the generated POM.")
	val pomIncludeRepository = SettingKey[MavenRepository => Boolean]("pom-include-repository", "Selects repositories to include in the generated POM.")
	val pomAllRepositories = SettingKey[Boolean]("pom-all-repositories", "If true, includes repositories used in module configurations in the pom repositories section.  If false, only the common repositories are included.")

	val moduleName = SettingKey[String]("module-name", "The name of the current module, used for dependency management.")
	val version = SettingKey[String]("version", "The version/revision of the current module.")
	val moduleID = SettingKey[ModuleID]("module-id", "A dependency management descriptor.  This is currently used for associating a ModuleID with a classpath entry.")
	val projectID = SettingKey[ModuleID]("project-id", "The dependency management descriptor for the current module.")
	val externalResolvers = TaskKey[Seq[Resolver]]("external-resolvers", "The external resolvers for automatically managed dependencies.")
	val resolvers = SettingKey[Seq[Resolver]]("resolvers", "The user-defined additional resolvers for automatically managed dependencies.")
	val projectResolver = TaskKey[Resolver]("project-resolver", "Resolver that handles inter-project dependencies.")
	val fullResolvers = TaskKey[Seq[Resolver]]("full-resolvers", "Combines the project resolver, default resolvers, and user-defined resolvers.")
	val otherResolvers = SettingKey[Seq[Resolver]]("other-resolvers", "Resolvers not included in the main resolver chain, such as those in module configurations.")
	val moduleConfigurations = SettingKey[Seq[ModuleConfiguration]]("module-configurations", "Defines module configurations, which override resolvers on a per-module basis.")
	val retrievePattern = SettingKey[String]("retrieve-pattern", "Pattern used to retrieve managed dependencies to the current build.")
	val retrieveConfiguration = SettingKey[Option[RetrieveConfiguration]]("retrieve-configuration", "Configures retrieving dependencies to the current build.")
	val offline = SettingKey[Boolean]("offline", "Configures sbt to work without a network connection where possible.")
	val ivyPaths = SettingKey[IvyPaths]("ivy-paths", "Configures paths used by Ivy for dependency management.")
	val libraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies", "Declares managed dependencies.")
	val allDependencies = TaskKey[Seq[ModuleID]]("all-dependencies", "Inter-project and library dependencies.")
	val projectDependencies = TaskKey[Seq[ModuleID]]("project-dependencies", "Inter-project dependencies.")
	val ivyXML = SettingKey[NodeSeq]("ivy-xml", "Defines inline Ivy XML for configuring dependency management.")
	val ivyScala = SettingKey[Option[IvyScala]]("ivy-scala", "Configures how Scala dependencies are checked, filtered, and injected.")
	val ivyValidate = SettingKey[Boolean]("ivy-validate", "Enables/disables Ivy validation of module metadata.")
	val ivyLoggingLevel = SettingKey[UpdateLogging.Value]("ivy-logging-level", "The logging level for updating.")
	val publishTo = SettingKey[Option[Resolver]]("publish-to", "The resolver to publish to.")
	val artifacts = SettingKey[Seq[Artifact]]("artifacts", "The artifact definitions for the current module.  Must be consistent with " + packagedArtifacts.key.label + ".")
	val projectDescriptors = TaskKey[Map[ModuleRevisionId,ModuleDescriptor]]("project-descriptors", "Project dependency map for the inter-project resolver.")
	val autoUpdate = SettingKey[Boolean]("auto-update", "<unimplemented>")
	val retrieveManaged = SettingKey[Boolean]("retrieve-managed", "If true, enables retrieving dependencies to the current build.  Otherwise, dependencies are used directly from the cache.")
	val managedDirectory = SettingKey[File]("managed-directory", "Directory to which managed dependencies are retrieved.")
	val classpathTypes = SettingKey[Set[String]]("classpath-types", "Artifact types that are included on the classpath.")
	val publishArtifact = SettingKey[Boolean]("publish-artifact", "Enables (true) or disables (false) publishing an artifact.")
	val packagedArtifact = TaskKey[(Artifact, File)]("packaged-artifact", "Generates a packaged artifact, returning the Artifact and the produced File.")
	val checksums = SettingKey[Seq[String]]("checksums", "The list of checksums to generate and to verify for dependencies.")

	val classifiersModule = TaskKey[GetClassifiersModule]("classifiers-module")
	val conflictWarning = SettingKey[ConflictWarning]("conflict-warning", "Configures warnings for conflicts in dependency management.")
	val autoScalaLibrary = SettingKey[Boolean]("auto-scala-library", "Adds a dependency on scala-library if true.")
	val sbtResolver = SettingKey[Resolver]("sbt-resolver", "Provides a resolver for obtaining sbt as a dependency.")
	val sbtDependency = SettingKey[ModuleID]("sbt-dependency", "Provides a definition for declaring the current version of sbt.")
	val sbtVersion = SettingKey[String]("sbt-version", "Provides the version of sbt.  This setting should be not be modified.")
	val skip = TaskKey[Boolean]("skip")

	// special
	val parallelExecution = SettingKey[Boolean]("parallel-execution", "Enables (true) or disables (false) parallel execution of tasks.")
	val settings = TaskKey[Settings[Scope]]("settings", "Provides access to the project data for the build.")
	val streams = TaskKey[TaskStreams]("streams", "Provides streams for logging and persisting data.")
	val isDummyTask = AttributeKey[Boolean]("is-dummy-task", "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.")
	val taskDefinitionKey = AttributeKey[ScopedKey[_]]("task-definition-key", "Internal: used to map a task back to its ScopedKey.")
	val (state, dummyState) = dummy[State]("state", "Current build state.")
	val (streamsManager, dummyStreamsManager) = dummy[Streams]("streams-manager", "Streams manager, which provides streams for different contexts.")
	val resolvedScoped = SettingKey[ScopedKey[_]]("resolved-scoped", "The ScopedKey for the referencing setting or task.")
	private[sbt] val parseResult: TaskKey[Any] = TaskKey("$parse-result", "Internal: used to implement input tasks.")

	val triggeredBy = AttributeKey[Seq[Task[_]]]("triggered-by")
	val runBefore = AttributeKey[Seq[Task[_]]]("run-before")

	type Streams = std.Streams[ScopedKey[_]]
	type TaskStreams = std.TaskStreams[ScopedKey[_]]

	def dummy[T: Manifest](name: String, description: String): (TaskKey[T], Task[T]) = (TaskKey[T](name, description), dummyTask(name))
	def dummyTask[T](name: String): Task[T] =
	{
		val base: Task[T] = task( error("Dummy task '" + name + "' did not get converted to a full task.") ) named name
		base.copy(info = base.info.set(isDummyTask, true))
	}
	def isDummy(t: Task[_]): Boolean = t.info.attributes.get(isDummyTask) getOrElse false
}
