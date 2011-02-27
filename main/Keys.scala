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
	// Path Keys
	val Base = SettingKey[File]("base-directory")
	val Target = SettingKey[File]("target")
	val Source = SettingKey[File]("source-directory")
	val SourceManaged = SettingKey[File]("source-managed-directory")
	val ScalaSource = SettingKey[File]("scala-source-directory")
	val JavaSource = SettingKey[File]("java-source-directory")
	val JavaSourceRoots = SettingKey[Seq[File]]("java-source-directories")
	val ResourceDir = SettingKey[File]("resource-directory")
	val SourceDirectories = SettingKey[Seq[File]]("source-directories")
	val ResourceDirectories = SettingKey[Seq[File]]("resource-directories")
	val ClassDirectory = SettingKey[File]("classes-directory")
	val DocDirectory = SettingKey[File]("doc-directory")
	val CacheDirectory = SettingKey[File]("cache-directory")
	val LibDirectory = SettingKey[File]("lib-directory")
	val SourceFilter = SettingKey[FileFilter]("source-filter")
	val DefaultExcludes = SettingKey[FileFilter]("default-excludes")
	val Sources = TaskKey[Seq[File]]("sources")
	val CleanFiles = SettingKey[Seq[File]]("clean-files")
	val CrossPaths = SettingKey[Boolean]("cross-paths")

	// compile/doc keys
	val MaxErrors = SettingKey[Int]("maximum-errors")
	val ScaladocOptions = SettingKey[Seq[String]]("scaladoc-options")
	val ScalacOptions = SettingKey[Seq[String]]("scalac-options")
	val JavacOptions = SettingKey[Seq[String]]("javac-options")
	val InitialCommands = SettingKey[String]("initial-commands")
	val CompileInputs = TaskKey[Compile.Inputs]("compile-inputs")
	val ScalaInstance = SettingKey[ScalaInstance]("scala-instance")
	val ScalaVersion = SettingKey[String]("scala-version")

	val WebappDir = SettingKey[File]("webapp-dir")

	val Clean = TaskKey[Unit]("clean")
	val ConsoleTask = TaskKey[Unit]("console")
	val ConsoleQuick = TaskKey[Unit]("console-quick")
	val ConsoleProject = TaskKey[Unit]("console-project")
	val CompileTask = TaskKey[Analysis]("compile")
	val Compilers = TaskKey[Compile.Compilers]("compilers")
	val DocTask = TaskKey[File]("doc")
	val CopyResources = TaskKey[Traversable[(File,File)]]("copy-resources")
	val Resources = TaskKey[Seq[File]]("resources")
	val Aggregate = SettingKey[Aggregation]("aggregate")
	
	// package keys
	val Package = TaskKey[sbt.Package.Configuration]("package")
	val PackageDoc = TaskKey[sbt.Package.Configuration]("package-doc")
	val PackageSrc = TaskKey[sbt.Package.Configuration]("package-src")
	val PackageOptions = TaskKey[Seq[PackageOption]]("package-options")
	val JarPath = SettingKey[File]("jar-path")
	val JarName = SettingKey[ArtifactName]("jar-name")
	val JarType = SettingKey[String]("jar-type")
	val NameToString = SettingKey[ArtifactName => String]("artifact-name-to-string")
	val Mappings = TaskKey[Seq[(File,String)]]("input-mappings")

	// Run Keys
	val SelectMainClass = TaskKey[Option[String]]("select-main-class")
	val MainClass = TaskKey[Option[String]]("main-class")
	val RunTask = InputKey[Unit]("run")
	val DiscoveredMainClasses = TaskKey[Seq[String]]("discovered-main-classes")
	val Runner = SettingKey[ScalaRun]("runner")

	val Fork = SettingKey[Boolean]("fork")
	val OutputStrategy = SettingKey[Option[sbt.OutputStrategy]]("output-strategy")
	val JavaHome = SettingKey[Option[File]]("java-home")
	val JavaOptions = SettingKey[Seq[String]]("java-options")

	// Test Keys
	val TestLoader = TaskKey[ClassLoader]("test-loader")
	val LoadedTestFrameworks = TaskKey[Map[TestFramework,Framework]]("loaded-test-frameworks")
	val DefinedTests = TaskKey[Seq[TestDefinition]]("defined-tests")
	val ExecuteTests = TaskKey[Test.Output]("execute-tests")
	val TestTask = TaskKey[Unit]("test")
	val TestOnly = InputKey[Unit]("test-only")
	val TestOptions = TaskKey[Seq[TestOption]]("test-options")
	val TestFrameworks = SettingKey[Seq[TestFramework]]("test-frameworks")
	val TestListeners = TaskKey[Iterable[TestReportListener]]("test-listeners")
		
	// Classpath/Dependency Management Keys
	type Classpath = Seq[Attributed[File]]
	
	val Name = SettingKey[String]("name")
	val NormalizedName = SettingKey[String]("normalized-name")
	val Organization = SettingKey[String]("organization")
	val DefaultConfiguration = SettingKey[Option[Configuration]]("default-configuration")
	val DefaultConfigurationMapping = SettingKey[String]("default-configuration-mapping")

	val Products = TaskKey[Classpath]("products")
	val UnmanagedClasspath = TaskKey[Classpath]("unmanaged-classpath")
	val UnmanagedJars = TaskKey[Classpath]("unmanaged-jars")
	val ManagedClasspath = TaskKey[Classpath]("managed-classpath")
	val InternalDependencyClasspath = TaskKey[Classpath]("internal-dependency-classpath")
	val ExternalDependencyClasspath = TaskKey[Classpath]("external-dependency-classpath")
	val DependencyClasspath = TaskKey[Classpath]("dependency-classpath")
	val FullClasspath = TaskKey[Classpath]("full-classpath")
	
	val IvyConfig = TaskKey[IvyConfiguration]("ivy-configuration")
	val ModuleSettingsTask = TaskKey[ModuleSettings]("module-settings")
	val UnmanagedBase = SettingKey[File]("unmanaged-base")
	val UpdateConfig = SettingKey[UpdateConfiguration]("update-configuration")
	val IvySbtTask = TaskKey[IvySbt]("ivy-sbt")
	val IvyModule = TaskKey[IvySbt#Module]("ivy-module")
	val ClasspathFilter = SettingKey[FileFilter]("classpath-filter")
	val Update = TaskKey[Map[String,Seq[File]]]("update")
	
	val PublishConfig = TaskKey[PublishConfiguration]("publish-configuration")
	val PublishLocalConfig = TaskKey[PublishConfiguration]("publish-local-configuration")
	val MakePomConfig = SettingKey[MakePomConfiguration]("make-pom-configuration")
	val PackageToPublish = TaskKey[Unit]("package-to-publish")
	val DeliverDepends = TaskKey[Unit]("deliver-depends")
	val PublishMavenStyle = SettingKey[Boolean]("publish-maven-style")

	val MakePom = TaskKey[File]("make-pom")
	val Deliver = TaskKey[Unit]("deliver")
	val DeliverLocal = TaskKey[Unit]("deliver-local")
	val Publish = TaskKey[Unit]("publish")
	val PublishLocal = TaskKey[Unit]("publish-local")

	val ModuleName = SettingKey[String]("module-id")
	val Version = SettingKey[String]("version")
	val ProjectID = SettingKey[ModuleID]("project-id")
	val BaseResolvers = SettingKey[Seq[Resolver]]("base-resolvers")
	val ProjectResolver = TaskKey[Resolver]("project-resolver")
	val Resolvers = TaskKey[Seq[Resolver]]("resolvers")
	val OtherResolvers = SettingKey[Seq[Resolver]]("other-resolvers")
	val ModuleConfigurations = SettingKey[Seq[ModuleConfiguration]]("module-configurations")
	val RetrievePattern = SettingKey[String]("retrieve-pattern")
	val RetrieveConfig = SettingKey[Option[RetrieveConfiguration]]("retrieve-configuration")
	val Offline = SettingKey[Boolean]("offline")
	val PathsIvy = SettingKey[IvyPaths]("ivy-paths")
	val LibraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies")
	val AllDependencies = TaskKey[Seq[ModuleID]]("all-dependencies")
	val ProjectDependencies = TaskKey[Seq[ModuleID]]("project-dependencies")
	val IvyXML = SettingKey[NodeSeq]("ivy-xml")
	val IvyScalaConfig = SettingKey[Option[IvyScala]]("ivy-scala-configuration")
	val IvyValidate = SettingKey[Boolean]("ivy-validate")
	val IvyLoggingLevel = SettingKey[UpdateLogging.Value]("ivy-logging-level")
	val PublishTo = SettingKey[Option[Resolver]]("publish-to")
	val PomName = SettingKey[ArtifactName]("pom-name")
	val PomFile = SettingKey[File]("pom-file")
	val PomArtifact = SettingKey[Seq[Artifact]]("pom-artifact")
	val Artifacts = SettingKey[Seq[Artifact]]("artifacts")
	val ProjectDescriptors = TaskKey[Map[ModuleRevisionId,ModuleDescriptor]]("project-descriptor-map")
	val AutoUpdate = SettingKey[Boolean]("auto-update")
	
	// special
	val Data = TaskKey[Settings[Scope]]("settings")
}