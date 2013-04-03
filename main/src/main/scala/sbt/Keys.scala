/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URL
	import Def.ScopedKey
	import complete._
	import inc.Analysis
	import inc.Locate.DefinesClass
	import std.TaskExtra._
	import xsbti.compile.{CompileOrder, GlobalsCache}
	import scala.xml.{Node => XNode, NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import testing.Framework
	import Configurations.CompilerPlugin
	import Types.Id
	import KeyRanks._

object Keys
{
	val TraceValues = "-1 to disable, 0 for up to the first sbt frame, or a positive number to set the maximum number of frames shown."

	// logging
	val logLevel = SettingKey[Level.Value]("log-level", "The amount of logging sent to the screen.", ASetting)
	val persistLogLevel = SettingKey[Level.Value]("persist-log-level", "The amount of logging sent to a file for persistence.", CSetting)
	val traceLevel = SettingKey[Int]("trace-level", "The amount of a stack trace displayed.  " + TraceValues, ASetting)
	val persistTraceLevel = SettingKey[Int]("persist-trace-level", "The amount of stack trace persisted.", CSetting)
	val showSuccess = SettingKey[Boolean]("show-success", "If true, displays a success message after running a command successfully.", CSetting)
	val showTiming = SettingKey[Boolean]("show-timing", "If true, the command success message includes the completion time.", CSetting)
	val timingFormat = SettingKey[java.text.DateFormat]("timing-format", "The format used for displaying the completion time.", CSetting)
	val extraLoggers = SettingKey[ScopedKey[_] => Seq[AbstractLogger]]("extra-loggers", "A function that provides additional loggers for a given setting.", DSetting)
	val logManager = SettingKey[LogManager]("log-manager", "The log manager, which creates Loggers for different contexts.", DSetting)
	val logBuffered = SettingKey[Boolean]("log-buffered", "True if logging should be buffered until work completes.", CSetting)

	// Project keys
	val projectCommand = AttributeKey[Boolean]("project-command", "Marks Commands that were registered for the current Project.", Invisible)
	val sessionSettings = AttributeKey[SessionSettings]("session-settings", "Tracks current build, project, and setting modifications.", DSetting)
	val stateBuildStructure = AttributeKey[BuildStructure]("build-structure", "Data structure containing all information about the build definition.", BSetting)
	val buildStructure = TaskKey[BuildStructure]("build-structure", "Provides access to the build structure, settings, and streams manager.", DTask)
	val loadedBuild = SettingKey[LoadedBuild]("loaded-build", "Provides access to the loaded project structure.  This is the information available before settings are evaluated.", DSetting)
	val buildDependencies = SettingKey[BuildDependencies]("build-dependencies", "Definitive source of inter-project dependencies for compilation and dependency management.\n\tThis is populated by default by the dependencies declared on Project instances, but may be modified.\n\tThe main restriction is that new builds may not be introduced.", DSetting)
	val appConfiguration = SettingKey[xsbti.AppConfiguration]("app-configuration", "Provides access to the launched sbt configuration, including the ScalaProvider, Launcher, and GlobalLock.", DSetting)
	val thisProject = SettingKey[ResolvedProject]("this-project", "Provides the current project for the referencing scope.", CSetting)
	val thisProjectRef = SettingKey[ProjectRef]("this-project-ref", "Provides a fully-resolved reference to the current project for the referencing scope.", CSetting)
	val configuration = SettingKey[Configuration]("configuration", "Provides the current configuration of the referencing scope.", CSetting)
	val commands = SettingKey[Seq[Command]]("commands", "Defines commands to be registered when this project or build is the current selected one.", CSetting)
	val initialize = SettingKey[Unit]("initialize", "A convenience setting for performing side-effects during initialization.", BSetting)
	val onLoad = SettingKey[State => State]("on-load", "Transformation to apply to the build state when the build is loaded.", DSetting)
	val onUnload = SettingKey[State => State]("on-unload", "Transformation to apply to the build state when the build is unloaded.", DSetting)
	val onLoadMessage = SettingKey[String]("on-load-message", "Message to display when the project is loaded.", DSetting)
	val transformState = AttributeKey[State => State]("transform-state", "State transformation to apply after tasks run.", DSetting)

	val onComplete = SettingKey[() => Unit]("on-complete", "Hook to run when task evaluation completes.  The type of this setting is subject to change, pending the resolution of SI-2915.", DSetting)
// https://issues.scala-lang.org/browse/SI-2915
//	val onComplete = SettingKey[RMap[Task,Result] => RMap[Task,Result]]("on-complete", "Transformation to apply to the final task result map.  This may also be used to register hooks to run when task evaluation completes.", DSetting)

	// Command keys
	val historyPath = SettingKey(BasicKeys.historyPath)
	val shellPrompt = SettingKey(BasicKeys.shellPrompt)
	val analysis = AttributeKey[inc.Analysis]("analysis", "Analysis of compilation, including dependencies and generated outputs.", DSetting)
	val watch = SettingKey(BasicKeys.watch)
	val pollInterval = SettingKey[Int]("poll-interval", "Interval between checks for modified sources by the continuous execution command.", BMinusSetting)
	val watchSources = TaskKey[Seq[File]]("watch-sources", "Defines the sources in this project for continuous execution to watch for changes.", BMinusSetting)
	val watchTransitiveSources = TaskKey[Seq[File]]("watch-transitive-sources", "Defines the sources in all projects for continuous execution to watch.", CSetting)
	val watchingMessage = SettingKey[WatchState => String]("watching-message", "The message to show when triggered execution waits for sources to change.", DSetting)
	val triggeredMessage = SettingKey[WatchState => String]("triggered-message", "The message to show before triggered execution executes an action after sources change.", DSetting)

	// Path Keys
	val baseDirectory = SettingKey[File]("base-directory", "The base directory.  Depending on the scope, this is the base directory for the build, project, configuration, or task.", AMinusSetting)
	val target = SettingKey[File]("target", "Main directory for files generated by the build.", AMinusSetting)
	val crossTarget = SettingKey[File]("cross-target", "Main directory for files generated by the build that are cross-built.", BSetting)

		// Source paths
	val sourceDirectory = SettingKey[File]("source-directory", "Default directory containing sources.", AMinusSetting)
	val sourceManaged = SettingKey[File]("source-managed", "Default directory for sources generated by the build.", BPlusSetting)
	val scalaSource = SettingKey[File]("scala-source", "Default Scala source directory.", ASetting)
	val javaSource = SettingKey[File]("java-source", "Default Java source directory.", ASetting)
	val sourceDirectories = SettingKey[Seq[File]]("source-directories", "List of all source directories, both managed and unmanaged.", AMinusSetting)
	val unmanagedSourceDirectories = SettingKey[Seq[File]]("unmanaged-source-directories", "Unmanaged source directories, which contain manually created sources.", ASetting)
	val unmanagedSources = TaskKey[Seq[File]]("unmanaged-sources", "Unmanaged sources, which are manually created.", BPlusTask)
	val managedSourceDirectories = SettingKey[Seq[File]]("managed-source-directories", "Managed source directories, which contain sources generated by the build.", BSetting)
	val managedSources = TaskKey[Seq[File]]("managed-sources", "Sources generated by the build.", BTask)
	val sources = TaskKey[Seq[File]]("sources", "All sources, both managed and unmanaged.", BTask)
	val sourcesInBase = SettingKey[Boolean]("sources-in-base", "If true, sources from the project's base directory are included as main sources.")

		// Filters
	val includeFilter = SettingKey[FileFilter]("include-filter", "Filter for including sources and resources files from default directories.", CSetting)
	val excludeFilter = SettingKey[FileFilter]("exclude-filter", "Filter for excluding sources and resources files from default directories.", CSetting)

		// Resource paths
	val resourceDirectory = SettingKey[File]("resource-directory", "Default unmanaged resource directory, used for user-defined resources.", ASetting)
	val resourceManaged = SettingKey[File]("resource-managed", "Default managed resource directory, used when generating resources.", BSetting)
	val unmanagedResourceDirectories = SettingKey[Seq[File]]("unmanaged-resource-directories", "Unmanaged resource directories, containing resources manually created by the user.", AMinusSetting)
	val unmanagedResources = TaskKey[Seq[File]]("unmanaged-resources", "Unmanaged resources, which are manually created.", BPlusTask)
	val managedResourceDirectories = SettingKey[Seq[File]]("managed-resource-directories", "List of managed resource directories.", AMinusSetting)
	val managedResources = TaskKey[Seq[File]]("managed-resources", "Resources generated by the build.", BTask)
	val resourceDirectories = SettingKey[Seq[File]]("resource-directories", "List of all resource directories, both managed and unmanaged.", BPlusSetting)
	val resources = TaskKey[Seq[File]]("resources", "All resource files, both managed and unmanaged.", BTask)

		// Output paths
	val classDirectory = SettingKey[File]("class-directory", "Directory for compiled classes and copied resources.", AMinusSetting)
	@deprecated("Use the cacheDirectory provided by streams.", "0.13.0")
	val cacheDirectory = SettingKey[File]("cache-directory", "Directory used for caching task data.", BMinusSetting)
	val cleanFiles = SettingKey[Seq[File]]("clean-files", "The files to recursively delete during a clean.", BSetting)
	val cleanKeepFiles = SettingKey[Seq[File]]("clean-keep-files", "Files to keep during a clean.", CSetting)
	val crossPaths = SettingKey[Boolean]("cross-paths", "If true, enables cross paths, which distinguish output directories for cross-building.", ASetting)
	val taskTemporaryDirectory = SettingKey[File]("task-temporary-directory", "Directory used for temporary files for tasks that is deleted after each task execution.", DSetting)

		// Generators
	val sourceGenerators = SettingKey[Seq[Task[Seq[File]]]]("source-generators", "List of tasks that generate sources.", CSetting)
	val resourceGenerators = SettingKey[Seq[Task[Seq[File]]]]("resource-generators", "List of tasks that generate resources.", CSetting)

	// compile/doc keys
	val autoCompilerPlugins = SettingKey[Boolean]("auto-compiler-plugins", "If true, enables automatically generating -Xplugin arguments to the compiler based on the classpath for the " + CompilerPlugin.name + " configuration.", AMinusSetting)
	val maxErrors = SettingKey[Int]("max-errors", "The maximum number of errors, such as compile errors, to list.", ASetting)
	val scalacOptions = TaskKey[Seq[String]]("scalac-options", "Options for the Scala compiler.", BPlusTask)
	val javacOptions = TaskKey[Seq[String]]("javac-options", "Options for the Java compiler.", BPlusTask)
	val incOptions = TaskKey[sbt.inc.IncOptions]("inc-options", "Options for the incremental compiler.", BTask)
	val compileOrder = SettingKey[CompileOrder]("compile-order", "Configures the order in which Java and sources within a single compilation are compiled.  Valid values are: JavaThenScala, ScalaThenJava, or Mixed.", BPlusSetting)
	val initialCommands = SettingKey[String]("initial-commands", "Initial commands to execute when starting up the Scala interpreter.", AMinusSetting)
	val cleanupCommands = SettingKey[String]("cleanup-commands", "Commands to execute before the Scala interpreter exits.", BMinusSetting)
	val compileInputs = TaskKey[Compiler.Inputs]("compile-inputs", "Collects all inputs needed for compilation.", DTask)
	val scalaHome = SettingKey[Option[File]]("scala-home", "If Some, defines the local Scala installation to use for compilation, running, and testing.", ASetting)
	val scalaInstance = TaskKey[ScalaInstance]("scala-instance", "Defines the Scala instance to use for compilation, running, and testing.", DTask)
	val scalaOrganization = SettingKey[String]("scala-organization", "Organization/group ID of the Scala used in the project. Default value is 'org.scala-lang'. This is an advanced setting used for clones of the Scala Language. It should be disregarded in standard use cases.", CSetting)
	val scalaVersion = SettingKey[String]("scala-version", "The version of Scala used for building.", APlusSetting)
	val scalaBinaryVersion = SettingKey[String]("scala-binary-version", "The Scala version substring describing binary compatibility.", BPlusSetting)
	val crossScalaVersions = SettingKey[Seq[String]]("cross-scala-versions", "The versions of Scala used when cross-building.", BPlusSetting)
	val crossVersion = SettingKey[CrossVersion]("cross-version", "Configures handling of the Scala version when cross-building.", CSetting)
	val classpathOptions = SettingKey[ClasspathOptions]("classpath-options", "Configures handling of Scala classpaths.", DSetting)
	val definedSbtPlugins = TaskKey[Set[String]]("defined-sbt-plugins", "The set of names of Plugin implementations defined by this project.", CTask)
	val sbtPlugin = SettingKey[Boolean]("sbt-plugin", "If true, enables adding sbt as a dependency and auto-generation of the plugin descriptor file.", BMinusSetting)
	val printWarnings = TaskKey[Unit]("print-warnings", "Shows warnings from compilation, including ones that weren't printed initially.", BPlusTask)

	val clean = TaskKey[Unit]("clean", "Deletes files produced by the build, such as generated sources, compiled classes, and task caches.", APlusTask)
	val console = TaskKey[Unit]("console", "Starts the Scala interpreter with the project classes on the classpath.", APlusTask)
	val consoleQuick = TaskKey[Unit]("console-quick", "Starts the Scala interpreter with the project dependencies on the classpath.", ATask, console)
	val consoleProject = TaskKey[Unit]("console-project", "Starts the Scala interpreter with the sbt and the build definition on the classpath and useful imports.", AMinusTask)
	val compile = TaskKey[Analysis]("compile", "Compiles sources.", APlusTask)
	val compilers = TaskKey[Compiler.Compilers]("compilers", "Defines the Scala and Java compilers to use for compilation.", DTask)
	val compileIncSetup = TaskKey[Compiler.IncSetup]("inc-compile-setup", "Configures aspects of incremental compilation.", DTask)
	val compilerCache = TaskKey[GlobalsCache]("compiler-cache", "Cache of scala.tools.nsc.Global instances.  This should typically be cached so that it isn't recreated every task run.", DTask)
	val stateCompilerCache = AttributeKey[GlobalsCache]("compiler-cache", "Internal use: Global cache.")
	val definesClass = TaskKey[DefinesClass]("defines-class", "Internal use: provides a function that determines whether the provided file contains a given class.", Invisible)
	val doc = TaskKey[File]("doc", "Generates API documentation.", AMinusTask)
	val copyResources = TaskKey[Seq[(File,File)]]("copy-resources", "Copies resources to the output directory.", AMinusTask)
	val aggregate = SettingKey[Boolean]("aggregate", "Configures task aggregation.", BMinusSetting)
	val sourcePositionMappers = TaskKey[Seq[xsbti.Position => Option[xsbti.Position]]]("source-position-mappers", "Maps positions in generated source files to the original source it was generated from", DTask)

	// package keys
	val packageBin = TaskKey[File]("package-bin", "Produces a main artifact, such as a binary jar.", ATask)
	val `package` = TaskKey[File]("package", "Produces the main artifact, such as a binary jar.  This is typically an alias for the task that actually does the packaging.", APlusTask)
	val packageDoc = TaskKey[File]("package-doc", "Produces a documentation artifact, such as a jar containing API documentation.", AMinusTask)
	val packageSrc = TaskKey[File]("package-src", "Produces a source artifact, such as a jar containing sources and resources.", AMinusTask)

	val packageOptions = TaskKey[Seq[PackageOption]]("package-options", "Options for packaging.", BTask)
	val packageConfiguration = TaskKey[Package.Configuration]("package-configuration", "Collects all inputs needed for packaging.", DTask)
	val artifactPath = SettingKey[File]("artifact-path", "The location of a generated artifact.", BPlusSetting)
	val artifact = SettingKey[Artifact]("artifact", "Describes an artifact.", BMinusSetting)
	val artifactClassifier = SettingKey[Option[String]]("artifact-classifier", "Sets the classifier used by the default artifact definition.", BSetting)
	val artifactName = SettingKey[(ScalaVersion, ModuleID, Artifact) => String]("artifact-name", "Function that produces the artifact name from its definition.", CSetting)
	val mappings = TaskKey[Seq[(File,String)]]("mappings", "Defines the mappings from a file to a path, used by packaging, for example.", BTask)
	val fileMappings = TaskKey[Seq[(File,File)]]("file-mappings", "Defines the mappings from a file to a file, used for copying files, for example.", BMinusTask)

	// Run Keys
	val selectMainClass = TaskKey[Option[String]]("select-main-class", "Selects the main class to run.", BMinusTask)
	val mainClass = TaskKey[Option[String]]("main-class", "Defines the main class for packaging or running.", BPlusTask)
	val run = InputKey[Unit]("run", "Runs a main class, passing along arguments provided on the command line.", APlusTask)
	val runMain = InputKey[Unit]("run-main", "Runs the main class selected by the first argument, passing the remaining arguments to the main method.", ATask)
	val discoveredMainClasses = TaskKey[Seq[String]]("discovered-main-classes", "Auto-detects main classes.", BMinusTask)
	val runner = TaskKey[ScalaRun]("runner", "Implementation used to run a main class.", DTask)
	val trapExit = SettingKey[Boolean]("trap-exit", "If true, enables exit trapping and thread management for 'run'-like tasks.  This is currently only suitable for serially-executed 'run'-like tasks.", CSetting)

	val fork = SettingKey[Boolean]("fork", "If true, forks a new JVM when running.  If false, runs in the same JVM as the build.", ASetting)
	val outputStrategy = SettingKey[Option[sbt.OutputStrategy]]("output-strategy", "Selects how to log output when running a main class.", DSetting)
	val connectInput = SettingKey[Boolean]("connect-input", "If true, connects standard input when running a main class forked.", CSetting)
	val javaHome = SettingKey[Option[File]]("java-home", "Selects the Java installation used for compiling and forking.  If None, uses the Java installation running the build.", ASetting)
	val javaOptions = TaskKey[Seq[String]]("java-options", "Options passed to a new JVM when forking.", BPlusTask)
	val envVars = TaskKey[Map[String,String]]("envVars", "Environment variables used when forking a new JVM", BTask)

	// Test Keys
	val testLoader = TaskKey[ClassLoader]("test-loader", "Provides the class loader used for testing.", DTask)
	val loadedTestFrameworks = TaskKey[Map[TestFramework,Framework]]("loaded-test-frameworks", "Loads Framework definitions from the test loader.", DTask)
	val definedTests = TaskKey[Seq[TestDefinition]]("defined-tests", "Provides the list of defined tests.", BMinusTask)
	val definedTestNames = TaskKey[Seq[String]]("defined-test-names", "Provides the set of defined test names.", BMinusTask)
	val executeTests = TaskKey[Tests.Output]("execute-tests", "Executes all tests, producing a report.", CTask)
	val test = TaskKey[Unit]("test", "Executes all tests.", APlusTask)
	val testOnly = InputKey[Unit]("test-only", "Executes the tests provided as arguments or all tests if no arguments are provided.", ATask)
	val testQuick = InputKey[Unit]("test-quick", "Executes the tests that either failed before, were not run or whose transitive dependencies changed, among those provided as arguments.", ATask)
	val testOptions = TaskKey[Seq[TestOption]]("test-options", "Options for running tests.", BPlusTask)
	val testFrameworks = SettingKey[Seq[TestFramework]]("test-frameworks", "Registered, although not necessarily present, test frameworks.", CTask)
	val testListeners = TaskKey[Seq[TestReportListener]]("test-listeners", "Defines test listeners.", DTask)
	val testExecution = TaskKey[Tests.Execution]("test-execution", "Settings controlling test execution", DTask)
	val testFilter = TaskKey[Seq[String] => Seq[String => Boolean]]("test-filter", "Filter controlling whether the test is executed", DTask)
	val testGrouping = TaskKey[Seq[Tests.Group]]("test-grouping", "Collects discovered tests into groups. Whether to fork and the options for forking are configurable on a per-group basis.", BMinusTask)
	val isModule = AttributeKey[Boolean]("is-module", "True if the target is a module.", DSetting)

	// Classpath/Dependency Management Keys
	type Classpath = Def.Classpath

	val name = SettingKey[String]("name", "Project name.", APlusSetting)
	val normalizedName = SettingKey[String]("normalized-name", "Project name transformed from mixed case and spaces to lowercase and dash-separated.", BSetting)
	val description = SettingKey[String]("description", "Project description.", BSetting)
	val homepage = SettingKey[Option[URL]]("homepage", "Project homepage.", BSetting)
	val startYear = SettingKey[Option[Int]]("start-year", "Year in which the project started.", BMinusSetting)
	val licenses = SettingKey[Seq[(String, URL)]]("licenses", "Project licenses as (name, url) pairs.", BMinusSetting)
	val organization = SettingKey[String]("organization", "Organization/group ID.", APlusSetting)
	val organizationName = SettingKey[String]("organization-name", "Organization full/formal name.", BMinusSetting)
	val organizationHomepage = SettingKey[Option[URL]]("organization-homepage", "Organization homepage.", BMinusSetting)
	val apiURL = SettingKey[Option[URL]]("api-url", "Base URL for API documentation.", BMinusSetting)
	val entryApiURL = AttributeKey[URL]("entry-api-url", "Base URL for the API documentation for a classpath entry.")
	val apiMappings = TaskKey[Map[File,URL]]("api-mappings", "Mappings from classpath entry to API documentation base URL.", BMinusSetting)
	val autoAPIMappings = SettingKey[Boolean]("auto-api-mappings", "If true, automatically manages mappings to the API doc URL.", BMinusSetting)
	val scmInfo = SettingKey[Option[ScmInfo]]("scm-info", "Basic SCM information for the project.", BMinusSetting)
	val projectInfo = SettingKey[ModuleInfo]("project-info", "Addition project information like formal name, homepage, licenses etc.", CSetting)
	val defaultConfiguration = SettingKey[Option[Configuration]]("default-configuration", "Defines the configuration used when none is specified for a dependency.", CSetting)
	val defaultConfigurationMapping = SettingKey[String]("default-configuration-mapping", "Defines the mapping used for a simple, unmapped configuration definition.", CSetting)

	val products = TaskKey[Seq[File]]("products", "Build products that get packaged.", BMinusTask)
	val productDirectories = TaskKey[Seq[File]]("product-directories", "Base directories of build products.", CTask)
	val exportJars = SettingKey[Boolean]("export-jars", "Determines whether the exported classpath for this project contains classes (false) or a packaged jar (true).", BSetting)
	val exportedProducts = TaskKey[Classpath]("exported-products", "Build products that go on the exported classpath.", CTask)
	val unmanagedClasspath = TaskKey[Classpath]("unmanaged-classpath", "Classpath entries (deep) that are manually managed.", BPlusTask)
	val unmanagedJars = TaskKey[Classpath]("unmanaged-jars", "Classpath entries for the current project (shallow) that are manually managed.", BPlusTask)
	val managedClasspath = TaskKey[Classpath]("managed-classpath", "The classpath consisting of external, managed library dependencies.", BMinusTask)
	val internalDependencyClasspath = TaskKey[Classpath]("internal-dependency-classpath", "The internal (inter-project) classpath.", CTask)
	val externalDependencyClasspath = TaskKey[Classpath]("external-dependency-classpath", "The classpath consisting of library dependencies, both managed and unmanaged.", BMinusTask)
	val dependencyClasspath = TaskKey[Classpath]("dependency-classpath", "The classpath consisting of internal and external, managed and unmanaged dependencies.", BPlusTask)
	val fullClasspath = TaskKey[Classpath]("full-classpath", "The exported classpath, consisting of build products and unmanaged and managed, internal and external dependencies.", BPlusTask)

	val internalConfigurationMap = SettingKey[Configuration => Configuration]("internal-configuration-map", "Maps configurations to the actual configuration used to define the classpath.", CSetting)
	val classpathConfiguration = TaskKey[Configuration]("classpath-configuration", "The configuration used to define the classpath.", CTask)
	val ivyConfiguration = TaskKey[IvyConfiguration]("ivy-configuration", "General dependency management (Ivy) settings, such as the resolvers and paths to use.", DTask)
	val ivyConfigurations = SettingKey[Seq[Configuration]]("ivy-configurations", "The defined configurations for dependency management.  This may be different from the configurations for Project settings.", BSetting)
	val moduleSettings = TaskKey[ModuleSettings]("module-settings", "Module settings, which configure dependency management for a specific module, such as a project.", DTask)
	val unmanagedBase = SettingKey[File]("unmanaged-base", "The default directory for manually managed libraries.", ASetting)
	val updateConfiguration = SettingKey[UpdateConfiguration]("update-configuration", "Configuration for resolving and retrieving managed dependencies.", DSetting)
	val ivySbt = TaskKey[IvySbt]("ivy-sbt", "Provides the sbt interface to Ivy.", CTask)
	val ivyModule = TaskKey[IvySbt#Module]("ivy-module", "Provides the sbt interface to a configured Ivy module.", CTask)
	val update = TaskKey[UpdateReport]("update", "Resolves and optionally retrieves dependencies, producing a report.", ATask)
	val transitiveUpdate = TaskKey[Seq[UpdateReport]]("transitive-update", "UpdateReports for the internal dependencies of this project.", DTask)
	val updateClassifiers = TaskKey[UpdateReport]("update-classifiers", "Resolves and optionally retrieves classified artifacts, such as javadocs and sources, for dependency definitions, transitively.", BPlusTask, update)
	val transitiveClassifiers = SettingKey[Seq[String]]("transitive-classifiers", "List of classifiers used for transitively obtaining extra artifacts for sbt or declared dependencies.", BSetting)
	val updateSbtClassifiers = TaskKey[UpdateReport]("update-sbt-classifiers", "Resolves and optionally retrieves classifiers, such as javadocs and sources, for sbt, transitively.", BPlusTask, updateClassifiers)

	val publishConfiguration = TaskKey[PublishConfiguration]("publish-configuration", "Configuration for publishing to a repository.", DTask)
	val publishLocalConfiguration = TaskKey[PublishConfiguration]("publish-local-configuration", "Configuration for publishing to the local Ivy repository.", DTask)
	val publishM2Configuration = TaskKey[PublishConfiguration]("publish-m2-configuration", "Configuration for publishing to the local Maven repository.", DTask)
	val deliverConfiguration = TaskKey[DeliverConfiguration]("deliver-configuration", "Configuration for generating the finished Ivy file for publishing.", DTask)
	val deliverLocalConfiguration = TaskKey[DeliverConfiguration]("deliver-local-configuration", "Configuration for generating the finished Ivy file for local publishing.", DTask)
	val makePomConfiguration = SettingKey[MakePomConfiguration]("make-pom-configuration", "Configuration for generating a pom.", DSetting)
	val packagedArtifacts = TaskKey[Map[Artifact,File]]("packaged-artifacts", "Packages all artifacts for publishing and maps the Artifact definition to the generated file.", CTask)
	val publishMavenStyle = SettingKey[Boolean]("publish-maven-style", "Configures whether to generate and publish a pom (true) or Ivy file (false).", BSetting)
	val credentials = TaskKey[Seq[Credentials]]("credentials", "The credentials to use for updating and publishing.", BMinusTask)

	val makePom = TaskKey[File]("make-pom", "Generates a pom for publishing when publishing Maven-style.", BPlusTask)
	val deliver = TaskKey[File]("deliver", "Generates the Ivy file for publishing to a repository.", BTask)
	val deliverLocal = TaskKey[File]("deliver-local", "Generates the Ivy file for publishing to the local repository.", BTask)
	val publish = TaskKey[Unit]("publish", "Publishes artifacts to a repository.", APlusTask)
	val publishLocal = TaskKey[Unit]("publish-local", "Publishes artifacts to the local Ivy repository.", APlusTask)
	val publishM2 = TaskKey[Unit]("publish-m2", "Publishes artifacts to the local Maven repository.", ATask)

	val pomExtra = SettingKey[NodeSeq]("pom-extra", "Extra XML to insert into the generated POM.", BSetting)
	val pomPostProcess = SettingKey[XNode => XNode]("pom-post-process", "Transforms the generated POM.", CSetting)
	val pomIncludeRepository = SettingKey[MavenRepository => Boolean]("pom-include-repository", "Selects repositories to include in the generated POM.", CSetting)
	val pomAllRepositories = SettingKey[Boolean]("pom-all-repositories", "If true, includes repositories used in module configurations in the pom repositories section.  If false, only the common repositories are included.", BMinusSetting)

	val moduleName = SettingKey[String]("module-name", "The name of the current module, used for dependency management.", BSetting)
	val version = SettingKey[String]("version", "The version/revision of the current module.", APlusSetting)
	val isSnapshot = SettingKey[Boolean]("is-snapshot", "True if the the version of the project is a snapshot version.", BPlusSetting)
	val moduleID = SettingKey[ModuleID]("module-id", "A dependency management descriptor.  This is currently used for associating a ModuleID with a classpath entry.", BPlusSetting)
	val projectID = SettingKey[ModuleID]("project-id", "The dependency management descriptor for the current module.", BMinusSetting)
	val overrideBuildResolvers = SettingKey[Boolean]("override-build-resolvers", "Whether or not all the build resolvers should be overriden with what's defined from the launcher.", BMinusSetting)
	val bootResolvers = TaskKey[Option[Seq[Resolver]]]("boot-resolvers", "The resolvers used by the sbt launcher.", BMinusSetting)
	val appResolvers = SettingKey[Option[Seq[Resolver]]]("app-resolvers", "The resolvers configured for this application by the sbt launcher.", BMinusSetting)
	val externalResolvers = TaskKey[Seq[Resolver]]("external-resolvers", "The external resolvers for automatically managed dependencies.", BMinusSetting)
	val resolvers = SettingKey[Seq[Resolver]]("resolvers", "The user-defined additional resolvers for automatically managed dependencies.", BMinusTask)
	val projectResolver = TaskKey[Resolver]("project-resolver", "Resolver that handles inter-project dependencies.", DTask)
	val fullResolvers = TaskKey[Seq[Resolver]]("full-resolvers", "Combines the project resolver, default resolvers, and user-defined resolvers.", CTask)
	val otherResolvers = SettingKey[Seq[Resolver]]("other-resolvers", "Resolvers not included in the main resolver chain, such as those in module configurations.", CSetting)
	val moduleConfigurations = SettingKey[Seq[ModuleConfiguration]]("module-configurations", "Defines module configurations, which override resolvers on a per-module basis.", BMinusSetting)
	val retrievePattern = SettingKey[String]("retrieve-pattern", "Pattern used to retrieve managed dependencies to the current build.", DSetting)
	val retrieveConfiguration = SettingKey[Option[RetrieveConfiguration]]("retrieve-configuration", "Configures retrieving dependencies to the current build.", DSetting)
	val offline = SettingKey[Boolean]("offline", "Configures sbt to work without a network connection where possible.", ASetting)
	val ivyPaths = SettingKey[IvyPaths]("ivy-paths", "Configures paths used by Ivy for dependency management.", DSetting)
	val libraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies", "Declares managed dependencies.", APlusSetting)
	val dependencyOverrides = SettingKey[Set[ModuleID]]("dependency-overrides", "Declares managed dependency overrides.", BSetting)
	val allDependencies = TaskKey[Seq[ModuleID]]("all-dependencies", "Inter-project and library dependencies.", CTask)
	val projectDependencies = TaskKey[Seq[ModuleID]]("project-dependencies", "Inter-project dependencies.", DTask)
	val ivyXML = SettingKey[NodeSeq]("ivy-xml", "Defines inline Ivy XML for configuring dependency management.", BSetting)
	val ivyScala = SettingKey[Option[IvyScala]]("ivy-scala", "Configures how Scala dependencies are checked, filtered, and injected.", CSetting)
	val ivyValidate = SettingKey[Boolean]("ivy-validate", "Enables/disables Ivy validation of module metadata.", BSetting)
	val ivyLoggingLevel = SettingKey[UpdateLogging.Value]("ivy-logging-level", "The logging level for updating.", BSetting)
	val publishTo = SettingKey[Option[Resolver]]("publish-to", "The resolver to publish to.", ASetting)
	val artifacts = SettingKey[Seq[Artifact]]("artifacts", "The artifact definitions for the current module.  Must be consistent with " + packagedArtifacts.key.label + ".", BSetting)
	val projectDescriptors = TaskKey[Map[ModuleRevisionId,ModuleDescriptor]]("project-descriptors", "Project dependency map for the inter-project resolver.", DTask)
	val autoUpdate = SettingKey[Boolean]("auto-update", "<unimplemented>", Invisible)
	val retrieveManaged = SettingKey[Boolean]("retrieve-managed", "If true, enables retrieving dependencies to the current build.  Otherwise, dependencies are used directly from the cache.", BSetting)
	val managedDirectory = SettingKey[File]("managed-directory", "Directory to which managed dependencies are retrieved.", BSetting)
	val classpathTypes = SettingKey[Set[String]]("classpath-types", "Artifact types that are included on the classpath.", BSetting)
	val publishArtifact = SettingKey[Boolean]("publish-artifact", "Enables (true) or disables (false) publishing an artifact.", AMinusSetting)
	val packagedArtifact = TaskKey[(Artifact, File)]("packaged-artifact", "Generates a packaged artifact, returning the Artifact and the produced File.", CTask)
	val checksums = SettingKey[Seq[String]]("checksums", "The list of checksums to generate and to verify for dependencies.", BSetting)

	val classifiersModule = TaskKey[GetClassifiersModule]("classifiers-module", rank = CTask)
	val conflictWarning = SettingKey[ConflictWarning]("conflict-warning", "Configures warnings for conflicts in dependency management.", CSetting)
	val conflictManager = SettingKey[ConflictManager]("conflict-manager", "Selects the conflict manager to use for dependency management.", CSetting)
	val autoScalaLibrary = SettingKey[Boolean]("auto-scala-library", "Adds a dependency on scala-library if true.", ASetting)
	val managedScalaInstance = SettingKey[Boolean]("managed-scala-instance", "Automatically obtains Scala tools as managed dependencies if true.", BSetting)
	val sbtResolver = SettingKey[Resolver]("sbt-resolver", "Provides a resolver for obtaining sbt as a dependency.", BMinusSetting)
	val sbtDependency = SettingKey[ModuleID]("sbt-dependency", "Provides a definition for declaring the current version of sbt.", BMinusSetting)
	val sbtVersion = SettingKey[String]("sbt-version", "Provides the version of sbt.  This setting should be not be modified.", AMinusSetting)
	val sbtBinaryVersion = SettingKey[String]("sbt-binary-version", "Defines the binary compatibility version substring.", BPlusSetting)
	val skip = TaskKey[Boolean]("skip", "For tasks that support it (currently only 'compile' and 'update'), setting skip to true will force the task to not to do its work.  This exact semantics may vary by task.", BSetting)

	// special
	val sessionVars = AttributeKey[SessionVar.Map]("session-vars", "Bindings that exist for the duration of the session.", Invisible)
	val parallelExecution = SettingKey[Boolean]("parallel-execution", "Enables (true) or disables (false) parallel execution of tasks.", BMinusSetting)
	val tags = SettingKey[Seq[(Tags.Tag,Int)]]("tags", ConcurrentRestrictions.tagsKey.label, BSetting)
	val concurrentRestrictions = SettingKey[Seq[Tags.Rule]]("concurrent-restrictions", "Rules describing restrictions on concurrent task execution.", BSetting)
	val cancelable = SettingKey[Boolean]("cancelable", "Enables (true) or disables (false) the ability to interrupt task execution with CTRL+C.", BMinusSetting)
	val settingsData = std.FullInstance.settingsData
	val streams = TaskKey[TaskStreams]("streams", "Provides streams for logging and persisting data.", DTask)
	val isDummyTask = AttributeKey[Boolean]("is-dummy-task", "Internal: used to identify dummy tasks.  sbt injects values for these tasks at the start of task execution.", Invisible)
	val taskDefinitionKey = AttributeKey[ScopedKey[_]]("task-definition-key", "Internal: used to map a task back to its ScopedKey.", Invisible)
	val (executionRoots, dummyRoots)= dummy[Seq[ScopedKey[_]]]("execution-roots", "The list of root tasks for this task execution.  Roots are the top-level tasks that were directly requested to be run.")
	val (state, dummyState) = dummy[State]("state", "Current build state.")
	val (streamsManager, dummyStreamsManager) = dummy[Streams]("streams-manager", "Streams manager, which provides streams for different contexts.")
	val stateStreams = AttributeKey[Streams]("streams-manager", "Streams manager, which provides streams for different contexts.  Setting this on State will override the default Streams implementation.")
	val resolvedScoped = Def.resolvedScoped
	val pluginData = TaskKey[PluginData]("plugin-data", "Information from the plugin build needed in the main build definition.", DTask)

	val triggeredBy = Def.triggeredBy
	val runBefore = Def.runBefore

	type Streams = std.Streams[ScopedKey[_]]
	type TaskStreams = std.TaskStreams[ScopedKey[_]]

	def dummy[T: Manifest](name: String, description: String): (TaskKey[T], Task[T]) = (TaskKey[T](name, description, DTask), dummyTask(name))
	def dummyTask[T](name: String): Task[T] =
	{
		val base: Task[T] = task( sys.error("Dummy task '" + name + "' did not get converted to a full task.") ) named name
		base.copy(info = base.info.set(isDummyTask, true))
	}
	def isDummy(t: Task[_]): Boolean = t.info.attributes.get(isDummyTask) getOrElse false
}
