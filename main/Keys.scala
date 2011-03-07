/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import EvaluateTask.{resolvedScoped, streams}
	import complete._
	import inc.Analysis
	import std.TaskExtra._
	import scala.xml.NodeSeq
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import org.scalatools.testing.Framework

object Keys
{
	// logging
	val logLevel = SettingKey[Level.Value]("log-level")
	val persistLogLevel = SettingKey[Level.Value]("persist-log-level")
	val traceLevel = SettingKey[Int]("trace-level")
	val showSuccess = SettingKey[Boolean]("show-success")
	val showTiming = SettingKey[Boolean]("show-timing")
	val timingFormat = SettingKey[java.text.DateFormat]("timing-format")

	// Project keys
	val projectCommand = AttributeKey[Boolean]("project-command")
	val sessionSettings = AttributeKey[SessionSettings]("session-settings")
	val buildStructure = AttributeKey[Load.BuildStructure]("build-structure")
	val appConfiguration = SettingKey[xsbti.AppConfiguration]("app-configuration")
	val thisProject = SettingKey[ResolvedProject]("this-project")
	val thisProjectRef = SettingKey[ProjectRef]("this-project-ref")
	val configuration = SettingKey[Configuration]("configuration")
	val commands = SettingKey[Seq[Command]]("commands")

	// Command keys
	val logged = AttributeKey[Logger]("log")
	val historyPath = SettingKey[Option[File]]("history")
	val shellPrompt = SettingKey[State => String]("shell-prompt")
	val analysis = AttributeKey[inc.Analysis]("analysis")
	val watch = SettingKey[Watched]("watch")
	val pollInterval = SettingKey[Int]("poll-interval")
	val watchSources = TaskKey[Seq[File]]("watch-sources")
	val watchTransitiveSources = TaskKey[Seq[File]]("watch-transitive-sources")

	// Path Keys
	val baseDirectory = SettingKey[File]("base-directory")
	val target = SettingKey[File]("target")
	val sourceDirectory = SettingKey[File]("source-directory")
	val sourceManaged = SettingKey[File]("source-managed")
	val scalaSource = SettingKey[File]("scala-source")
	val javaSource = SettingKey[File]("java-source")
	val javaSourceRoots = SettingKey[Seq[File]]("java-source-roots")
	val resourceDirectory = SettingKey[File]("resource-directory")
	val sourceDirectories = SettingKey[Seq[File]]("source-directories")
	val resourceDirectories = SettingKey[Seq[File]]("resource-directories")
	val classDirectory = SettingKey[File]("class-directory")
	val docDirectory = SettingKey[File]("doc-directory")
	val cacheDirectory = SettingKey[File]("cache-directory")
	val sourceFilter = SettingKey[FileFilter]("source-filter")
	val defaultExcludes = SettingKey[FileFilter]("default-excludes")
	val sources = TaskKey[Seq[File]]("sources")
	val cleanFiles = SettingKey[Seq[File]]("clean-files")
	val crossPaths = SettingKey[Boolean]("cross-paths")

	// compile/doc keys
	val maxErrors = SettingKey[Int]("max-errors")
	val scaladocOptions = SettingKey[Seq[String]]("scaladoc-options")
	val scalacOptions = SettingKey[Seq[String]]("scalac-options")
	val javacOptions = SettingKey[Seq[String]]("javac-options")
	val initialCommands = SettingKey[String]("initial-commands")
	val compileInputs = TaskKey[Compiler.Inputs]("compile-inputs")
	val scalaInstance = SettingKey[ScalaInstance]("scala-instance")
	val scalaVersion = SettingKey[String]("scala-version")

	val webappDir = SettingKey[File]("webapp-dir")

	val clean = TaskKey[Unit]("clean")
	val console = TaskKey[Unit]("console")
	val consoleQuick = TaskKey[Unit]("console-quick")
	val consoleProject = TaskKey[Unit]("console-project")
	val compile = TaskKey[Analysis]("compile")
	val compilers = TaskKey[Compiler.Compilers]("compilers")
	val doc = TaskKey[File]("doc")
	val copyResources = TaskKey[Traversable[(File,File)]]("copy-resources")
	val resources = TaskKey[Seq[File]]("resources")
	val aggregate = SettingKey[Aggregation]("aggregate")
	
	// package keys
	val packageBin = TaskKey[Package.Configuration]("package")
	val packageDoc = TaskKey[Package.Configuration]("package-doc")
	val packageSrc = TaskKey[Package.Configuration]("package-src")
	val packageOptions = TaskKey[Seq[PackageOption]]("package-options")
	val jarPath = SettingKey[File]("jar-path")
	val jarName = SettingKey[ArtifactName]("jar-name")
	val jarType = SettingKey[String]("jar-type")
	val nameToString = SettingKey[ArtifactName => String]("name-to-string")
	val mappings = TaskKey[Seq[(File,String)]]("mappings")

	// Run Keys
	val selectMainClass = TaskKey[Option[String]]("select-main-class")
	val mainClass = TaskKey[Option[String]]("main-class")
	val run = InputKey[Unit]("run")
	val discoveredMainClasses = TaskKey[Seq[String]]("discovered-main-classes")
	val runner = SettingKey[ScalaRun]("runner")

	val fork = SettingKey[Boolean]("fork")
	val outputStrategy = SettingKey[Option[sbt.OutputStrategy]]("output-strategy")
	val javaHome = SettingKey[Option[File]]("java-home")
	val javaOptions = SettingKey[Seq[String]]("java-options")

	// Test Keys
	val testLoader = TaskKey[ClassLoader]("test-loader")
	val loadedTestFrameworks = TaskKey[Map[TestFramework,Framework]]("loaded-test-frameworks")
	val definedTests = TaskKey[Seq[TestDefinition]]("defined-tests")
	val executeTests = TaskKey[Tests.Output]("execute-tests")
	val test = TaskKey[Unit]("test")
	val testOnly = InputKey[Unit]("test-only")
	val testOptions = TaskKey[Seq[TestOption]]("test-options")
	val testFrameworks = SettingKey[Seq[TestFramework]]("test-frameworks")
	val testListeners = TaskKey[Iterable[TestReportListener]]("test-listeners")
		
	// Classpath/Dependency Management Keys
	type Classpath = Seq[Attributed[File]]
	
	val name = SettingKey[String]("name")
	val normalizedName = SettingKey[String]("normalized-name")
	val organization = SettingKey[String]("organization")
	val defaultConfiguration = SettingKey[Option[Configuration]]("default-configuration")
	val defaultConfigurationMapping = SettingKey[String]("default-configuration-mapping")

	val products = TaskKey[Classpath]("products")
	val unmanagedClasspath = TaskKey[Classpath]("unmanaged-classpath")
	val unmanagedJars = TaskKey[Classpath]("unmanaged-jars")
	val managedClasspath = TaskKey[Classpath]("managed-classpath")
	val internalDependencyClasspath = TaskKey[Classpath]("internal-dependency-classpath")
	val externalDependencyClasspath = TaskKey[Classpath]("external-dependency-classpath")
	val dependencyClasspath = TaskKey[Classpath]("dependency-classpath")
	val fullClasspath = TaskKey[Classpath]("full-classpath")
	
	val ivyConfiguration = TaskKey[IvyConfiguration]("ivy-configuration")
	val moduleSettings = TaskKey[ModuleSettings]("module-settings")
	val unmanagedBase = SettingKey[File]("unmanaged-base")
	val updateConfiguration = SettingKey[UpdateConfiguration]("update-configuration")
	val ivySbt = TaskKey[IvySbt]("ivy-sbt")
	val ivyModule = TaskKey[IvySbt#Module]("ivy-module")
	val classpathFilter = SettingKey[FileFilter]("classpath-filter")
	val update = TaskKey[Map[String,Seq[File]]]("update")
	
	val publishConfiguration = TaskKey[PublishConfiguration]("publish-configuration")
	val publishLocalConfiguration = TaskKey[PublishConfiguration]("publish-local-configuration")
	val makePomConfiguration = SettingKey[MakePomConfiguration]("make-pom-configuration")
	val packageToPublish = TaskKey[Unit]("package-to-publish")
	val deliverDepends = TaskKey[Unit]("deliver-depends")
	val publishMavenStyle = SettingKey[Boolean]("publish-maven-style")

	val makePom = TaskKey[File]("make-pom")
	val deliver = TaskKey[Unit]("deliver")
	val deliverLocal = TaskKey[Unit]("deliver-local")
	val publish = TaskKey[Unit]("publish")
	val publishLocal = TaskKey[Unit]("publish-local")

	val moduleID = SettingKey[String]("module-id")
	val version = SettingKey[String]("version")
	val projectID = SettingKey[ModuleID]("project-id")
	val baseResolvers = SettingKey[Seq[Resolver]]("base-resolvers")
	val projectResolver = TaskKey[Resolver]("project-resolver")
	val resolvers = TaskKey[Seq[Resolver]]("resolvers")
	val otherResolvers = SettingKey[Seq[Resolver]]("other-resolvers")
	val moduleConfigurations = SettingKey[Seq[ModuleConfiguration]]("module-configurations")
	val retrievePattern = SettingKey[String]("retrieve-pattern")
	val retrieveConfiguration = SettingKey[Option[RetrieveConfiguration]]("retrieve-configuration")
	val offline = SettingKey[Boolean]("offline")
	val ivyPaths = SettingKey[IvyPaths]("ivy-paths")
	val libraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies")
	val allDependencies = TaskKey[Seq[ModuleID]]("all-dependencies")
	val projectDependencies = TaskKey[Seq[ModuleID]]("project-dependencies")
	val ivyXML = SettingKey[NodeSeq]("ivy-xml")
	val ivyScala = SettingKey[Option[IvyScala]]("ivy-scala")
	val ivyValidate = SettingKey[Boolean]("ivy-validate")
	val ivyLoggingLevel = SettingKey[UpdateLogging.Value]("ivy-logging-level")
	val publishTo = SettingKey[Option[Resolver]]("publish-to")
	val pomName = SettingKey[ArtifactName]("pom-name")
	val pomFile = SettingKey[File]("pom-file")
	val pomArtifact = SettingKey[Seq[Artifact]]("pom-artifact")
	val artifacts = SettingKey[Seq[Artifact]]("artifacts")
	val projectDescriptors = TaskKey[Map[ModuleRevisionId,ModuleDescriptor]]("project-descriptors")
	val autoUpdate = SettingKey[Boolean]("auto-update")
	
	// special
	val settings = TaskKey[Settings[Scope]]("settings")
}