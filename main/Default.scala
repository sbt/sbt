/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Scope.{GlobalScope,ThisScope}
	import Project.{AppConfig, Config, Initialize, ScopedKey, Setting, ThisProject, ThisProjectRef}
	import Configurations.{Compile => CompileConf, Test => TestConf}
	import Command.HistoryPath
	import EvaluateTask.{resolvedScoped, streams}
	import complete._
	import inc.Analysis
	import std.TaskExtra._
	import scala.xml.{Node => XNode,NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import org.scalatools.testing.Framework
	import Types._


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
	val CompileTask = TaskKey[Analysis]("compile")
	val Compilers = TaskKey[Compile.Compilers]("compilers")
	val DocTask = TaskKey[File]("doc")
	val CopyResources = TaskKey[Traversable[(File,File)]]("copy-resources")
	val Resources = TaskKey[Seq[File]]("resources")
	
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
	val PomFile = SettingKey[File]("pom-file")
	val PomArtifact = SettingKey[Seq[Artifact]]("pom-artifact")
	val Artifacts = SettingKey[Seq[Artifact]]("artifacts")
	val ProjectDescriptors = TaskKey[Map[ModuleRevisionId,ModuleDescriptor]]("project-descriptor-map")
	val AutoUpdate = SettingKey[Boolean]("auto-update")
	
	// special
	val Data = TaskKey[Settings[Scope]]("settings")
}
object Default
{
	import Path._
	import GlobFilter._
	import Keys._
	implicit def richFileSetting(s: ScopedSetting[File]): RichFileSetting = new RichFileSetting(s)
	implicit def richFilesSetting(s: ScopedSetting[Seq[File]]): RichFilesSetting = new RichFilesSetting(s)
	
	final class RichFileSetting(s: ScopedSetting[File]) extends RichFileBase
	{
		def /(c: String): Initialize[File] = s { _ / c }
		protected[this] def map0(f: PathFinder => PathFinder) = s(file => finder(f)(file :: Nil))
	}
	final class RichFilesSetting(s: ScopedSetting[Seq[File]]) extends RichFileBase
	{
		def /(s: String): Initialize[Seq[File]] = map0 { _ / s }
		protected[this] def map0(f: PathFinder => PathFinder) = s(finder(f))
	}
	sealed abstract class RichFileBase
	{
		def *(filter: FileFilter): Initialize[Seq[File]] = map0 { _ * filter }
		def **(filter: FileFilter): Initialize[Seq[File]] = map0 { _ ** filter }
		protected[this] def map0(f: PathFinder => PathFinder): Initialize[Seq[File]]
		protected[this] def finder(f: PathFinder => PathFinder): Seq[File] => Seq[File] =
			in => f(in).getFiles.toSeq
	}
	def configSrcSub(key: ScopedSetting[File]): Initialize[File] = (key, Config) { (src, conf) => src / nameForSrc(conf.name) }
	def nameForSrc(config: String) = if(config == "compile") "main" else config
	def prefix(config: String) = if(config == "compile") "" else config + "-"
	def toSeq[T](key: ScopedSetting[T]): Initialize[Seq[T]] = key( _ :: Nil)

	def extractAnalysis[T](a: Attributed[T]): (T, Analysis) = 
		(a.data, a.metadata get Command.Analysis getOrElse Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, Analysis] =
		(cp map extractAnalysis).toMap

	def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)

	def core = Seq(
		Name :== "test",
		Version :== "0.1",
		MaxErrors in GlobalScope :== 100,
		Project.Commands :== Nil,
		Data <<= EvaluateTask.state map { state => Project.structure(state).data }
	)
	def paths = Seq(
		Base <<= ThisProject(_.base),
		Target <<= Base / "target",
		DefaultExcludes in GlobalScope :== (".*"  - ".") || HiddenFileFilter,
		HistoryPath <<= Target(t => Some(t / ".history")),
		CacheDirectory <<= Target / "cache",
		Source <<= Base / "src",
		SourceFilter in GlobalScope :== ("*.java" | "*.scala"),
		SourceManaged <<= Base / "src_managed"
	)

	lazy val configPaths = Seq(
		Source <<= configSrcSub( Source in Scope(This,Global,This,This) ),
		SourceManaged <<= configSrcSub(SourceManaged),
		CacheDirectory <<= (CacheDirectory, Config) { _ / _.name },
		ClassDirectory <<= (Target, Config) { (target, conf) => target / (prefix(conf.name) + "classes") },
		DocDirectory <<= (Target, Config) { (target, conf) => target / (prefix(conf.name) + "api") },
		Sources <<= (SourceDirectories, SourceFilter, DefaultExcludes) map { (d,f,excl) => d.descendentsExcept(f,excl).getFiles.toSeq },
		ScalaSource <<= Source / "scala",
		JavaSource <<= Source / "java",
		JavaSourceRoots <<= toSeq(JavaSource),
		ResourceDir <<= Source / "resources",
		SourceDirectories <<= (ScalaSource, JavaSourceRoots) { _ +: _ },
		ResourceDirectories <<= toSeq(ResourceDir),
		Resources <<= (ResourceDirectories, DefaultExcludes) map { (dirs, excl) => dirs.descendentsExcept("*",excl).getFiles.toSeq }
	)
	def addBaseSources = Seq(
		Sources <<= (Sources, Base, SourceFilter, DefaultExcludes) map { (srcs,b,f,excl) => (srcs +++ b * (f -- excl)).getFiles.toSeq }
	)
	lazy val configTasks = Classpaths.configSettings ++ baseTasks
	
	def webPaths = Seq(
		WebappDir <<= Source / "webapp"
	)

	def compileBase = Seq(
		Compilers <<= (ScalaInstance, AppConfig, streams) map { (si, app, s) => Compile.compilers(si)(app, s.log) },
		JavacOptions in GlobalScope :== Nil,
		ScalacOptions in GlobalScope :== Nil,
		ScalaInstance <<= (AppConfig, ScalaVersion){ (app, version) => sbt.ScalaInstance(version, app.provider.scalaProvider.launcher) },
		ScalaVersion <<= AppConfig( _.provider.scalaProvider.version),
		Target <<= (Target, ScalaInstance)( (t,si) => t / ("scala-" + si.actualVersion) )
	)

	def baseTasks = Seq(
		InitialCommands in GlobalScope :== "",
		CompileTask <<= compileTask,
		CompileInputs <<= compileInputsTask,
		ConsoleTask <<= console,
		ConsoleQuick <<= consoleQuick,
		DiscoveredMainClasses <<= CompileTask map discoverMainClasses,
		Runner <<= ScalaInstance( si => new Run(si) ),
		SelectMainClass <<= DiscoveredMainClasses map selectRunMain,
		MainClass in RunTask :== SelectMainClass,
		MainClass <<= DiscoveredMainClasses map selectPackageMain,
		RunTask <<= runTask(FullClasspath, MainClass in RunTask),
		ScaladocOptions <<= ScalacOptions(identity),
		DocTask <<= docTask,
		CopyResources <<= copyResources
	)

	lazy val projectTasks = Seq(
		CleanFiles <<= (Target, SourceManaged) { _ :: _ :: Nil },
		Clean <<= CleanFiles map IO.delete
	)

	lazy val testTasks = Seq(	
		TestLoader <<= (FullClasspath, ScalaInstance) map { (cp, si) => TestFramework.createTestLoader(data(cp), si) },
		TestFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs, ScalaTest, ScalaCheckCompat, ScalaTestCompat, SpecsCompat, JUnit)
		},
		LoadedTestFrameworks <<= (TestFrameworks, streams, TestLoader) map { (frameworks, s, loader) =>
			frameworks.flatMap(f => f.create(loader, s.log).map( x => (f,x)).toIterable).toMap
		},
		DefinedTests <<= (LoadedTestFrameworks, CompileTask, streams) map { (frameworkMap, analysis, s) =>
			val tests = Test.discover(frameworkMap.values.toSeq, analysis)._1
			IO.writeLines(s.text(CompletionsID), tests.map(_.name).distinct)
			tests
		},
		TestListeners <<= (streams in TestTask) map ( s => TestLogger(s.log) :: Nil ),
		TestOptions <<= TestListeners map { listeners => Test.Listeners(listeners) :: Nil },
		ExecuteTests <<= (streams in TestTask, LoadedTestFrameworks, TestOptions, TestLoader, DefinedTests, CopyResources) flatMap {
			(s, frameworkMap, options, loader, discovered, _) => Test(frameworkMap, loader, discovered, options, s.log)
		},
		TestTask <<= (ExecuteTests, streams) map { (results, s) => Test.showResults(s.log, results) },
		TestOnly <<= testOnly
	)

	def testOnly = 
	InputTask(resolvedScoped(testOnlyParser)) ( result =>  
		(streams, LoadedTestFrameworks, TestOptions, TestLoader, DefinedTests, CopyResources, result) flatMap {
			case (s, frameworks, opts, loader, discovered, _, (tests, frameworkOptions)) =>
				val modifiedOpts = Test.Filter(if(tests.isEmpty) _ => true else tests.toSet ) +: Test.Argument(frameworkOptions : _*) +: opts
				Test(frameworks, loader, discovered, modifiedOpts, s.log) map { results =>
					Test.showResults(s.log, results)
				}
		}
	)
	lazy val packageDefaults = packageBase ++ inConfig(CompileConf)(packageConfig) ++ inConfig(TestConf)(packageConfig)
	
	lazy val packageBase = Seq(
		jarName,
		PackageOptions in GlobalScope :== Nil,
		NameToString in GlobalScope :== (ArtifactName.show _)
	)
	lazy val packageConfig = Seq(
		JarName <<= (JarName, Config) { (n,c) => n.copy(config = c.name) },
		PackageOptions in Package <<= MainClass in Package map ( _.map(sbt.Package.MainClass.apply).toList )
	) ++
	packageTasks(Package, "", packageBin) ++
	packageTasks(PackageSrc, "src", packageSrc) ++
	packageTasks(PackageDoc, "doc", packageDoc)

	private[this] val allSubpaths = (_: File).###.***.xx.toSeq

	def packageBin = (CompileInputs, CompileTask) map { (in, _) => allSubpaths(in.config.classesDirectory) }
	def packageDoc = DocTask map allSubpaths
	def packageSrc = (Resources, ResourceDirectories, Sources, SourceDirectories, Base) map {
		(rs, rds, srcs, sds, base) => ( (rs x relativeTo(rds)) ++ (srcs x (relativeTo(sds)|relativeTo(base)) ) ).toSeq
	}
	def jarName  =  JarName <<= (ModuleName, Version) { (n,v) =>
		ArtifactName(base = n, version = v, config = "", tpe = "", ext = "jar")
	}
	def jarPath  =  JarPath <<= (Target, JarName, NameToString) { (t, n, toString) => t / toString(n) }

	def packageTasks(key: TaskKey[sbt.Package.Configuration], tpeString: String, mappings: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in ThisScope.copy(task = Global) <<= packageTask,
			Mappings <<= mappings,
			JarType :== tpeString,
			JarName <<= (JarType,JarName){ (tpe, name) => (name.copy(tpe = tpe)) },
			CacheDirectory <<= CacheDirectory / key.key.label,
			jarPath
		))
	def packageTask: Initialize[Task[sbt.Package.Configuration]] =
		(JarPath, Mappings, PackageOptions, CacheDirectory, streams) map { (jar, srcs, options, cacheDir, s) =>
			val config = new sbt.Package.Configuration(srcs, jar, options)
			sbt.Package(config, cacheDir, s.log)
			config
		}

	def selectRunMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(Some(SimpleReader readLine _), classes)
	def selectPackageMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(None, classes)

	def runTask(classpath: ScopedTask[Classpath], mainClass: ScopedTask[Option[String]]): Initialize[InputTask[Unit]] =
		InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
			(classpath, mainClass, Runner, streams, CopyResources, result) map { (cp, main, runner, s, _, args) =>
				val mainClass = main getOrElse error("No main class detected.")
				runner.run(mainClass, data(cp), args, s.log) foreach error
			}
		}

	def docTask: Initialize[Task[File]] =
		(CompileInputs, streams, DocDirectory, Config, ScaladocOptions) map { (in, s, target, config, options) =>
			val d = new Scaladoc(in.config.maxErrors, in.compilers.scalac)
			d(nameForSrc(config.name), in.config.sources, in.config.classpath, target, options)(s.log)
			target
		}

	def mainRunTask = RunTask <<= runTask(FullClasspath in Configurations.Runtime, MainClass in RunTask)

	def discoverMainClasses(analysis: Analysis): Seq[String] =
		compile.Discovery.applications(Test.allDefs(analysis)) collect { case (definition, discovered) if(discovered.hasMain) => definition.name }
	
	def console = consoleTask(FullClasspath, ConsoleTask)
	def consoleQuick = consoleTask(ExternalDependencyClasspath, ConsoleQuick)
	def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]) = (Compilers, classpath, ScalacOptions in task, InitialCommands in task, streams) map {
		(cs, cp, options, initialCommands, s) =>
			(new Console(cs.scalac))(data(cp), options, initialCommands, s.log).foreach(msg => error(msg))
			println()
	}
	
	def compileTask = (CompileInputs, streams) map { (i,s) => Compile(i,s.log) }
	def compileInputsTask =
		(DependencyClasspath, Sources, JavaSourceRoots, Compilers, JavacOptions, ScalacOptions, CacheDirectory, ClassDirectory, streams) map {
		(cp, sources, javaRoots, compilers, javacOptions, scalacOptions, cacheDirectory, classes, s) =>
			val classpath = classes +: data(cp)
			val analysis = analysisMap(cp)
			val cache = cacheDirectory / "compile"
			Compile.inputs(classpath, sources, classes, scalacOptions, javacOptions, javaRoots, analysis, cache, 100)(compilers, s.log)
		}

	def copyResources =
	(ClassDirectory, CacheDirectory, Resources, ResourceDirectories, streams) map { (target, cache, resources, dirs, s) =>
		val cacheFile = cache / "copy-resources"
		val mappings = resources x rebase(dirs, target)
		s.log.debug("Copy resource mappings: " + mappings.mkString("\n\t","\n\t",""))
		Sync(cacheFile)( mappings )
		mappings
	}

	def testOnlyParser(resolved: ScopedKey[_]): State => Parser[(Seq[String],Seq[String])] =
	{ state =>
			import DefaultParsers._
		def distinctParser(exs: Set[String]): Parser[Seq[String]] =
			token(Space ~> NotSpace.examples(exs)).flatMap(ex => distinctParser(exs - ex).map(ex +: _)) ?? Nil
		val tests = savedLines(state, resolved, DefinedTests)
		val selectTests = distinctParser(tests.toSet) // todo: proper IDs
		val options = (Space ~> "--" ~> spaceDelimited("<arg>")) ?? Nil
		selectTests ~ options
	}
	def savedLines(state: State, reader: ScopedKey[_], readFrom: Scoped): Seq[String] =
	{
		val structure = Project.structure(state)
		structure.data.definingScope(reader.scope, readFrom.key) match {
			case Some(defined) =>
				val key = ScopedKey(Scope.fillTaskAxis(defined, readFrom.key), readFrom.key)
				structure.streams.use(reader){ ts => IO.readLines(ts.readText(key, CompletionsID)) }
			case None => Nil
		}
	}

	val CompletionsID = "completions"

//	lazy val projectConsole = task { Console.sbtDefault(info.compileInputs, this)(ConsoleLogger()) }
		
	def inConfig(conf: Configuration)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(config = Select(conf)) )( (Config :== conf) +: ss)
	def inTask(t: Scoped)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		inScope(ThisScope.copy(task = Select(t.key)) )( ss )
	def inScope(scope: Scope)(ss: Seq[Setting[_]]): Seq[Setting[_]] =
		Project.transform(Scope.replaceThis(scope), ss)

	lazy val defaultPaths = paths ++ inConfig(CompileConf)(configPaths ++ addBaseSources) ++ inConfig(TestConf)(configPaths)
	lazy val defaultWebPaths = defaultPaths ++ inConfig(CompileConf)(webPaths)

	lazy val defaultTasks =
		projectTasks ++
		inConfig(CompileConf)(configTasks :+ mainRunTask) ++
		inConfig(TestConf)(configTasks ++ testTasks) ++
		packageDefaults

	lazy val defaultWebTasks = Nil

	def pluginDefinition = Seq(
		EvaluateTask.PluginDefinition <<= (FullClasspath in CompileConf,CompileTask in CompileConf) map ( (c,a) => (data(c),a) )
	)

	lazy val defaultClasspaths =
		Classpaths.publishSettings ++ Classpaths.baseSettings ++
		inConfig(CompileConf)(Classpaths.configSettings) ++
		inConfig(TestConf)(Classpaths.configSettings)


	lazy val defaultSettings = core ++ defaultPaths ++ defaultClasspaths ++ defaultTasks ++ compileBase ++ pluginDefinition
	lazy val defaultWebSettings = defaultSettings ++ defaultWebPaths ++ defaultWebTasks
}
object Classpaths
{
		import Path._
		import GlobFilter._
		import Keys._
		import Scope.ThisScope
		import Default._
		import Attributed.{blank, blankSeq}

	def concat[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] = (a,b) map (_ ++ _)

	val configSettings: Seq[Project.Setting[_]] = Seq(
		ExternalDependencyClasspath <<= concat(UnmanagedClasspath, ManagedClasspath),
		DependencyClasspath <<= concat(InternalDependencyClasspath, ExternalDependencyClasspath),
		FullClasspath <<= concat(DependencyClasspath, Products),
		InternalDependencyClasspath <<= internalDependencies,
		Products <<= makeProducts,
		ManagedClasspath <<= (Config, Update) map { (config, up) => up.getOrElse(config.name, error("Configuration '" + config.name + "' unresolved by 'update'.")) },
		UnmanagedClasspath <<= (Config, UnmanagedBase, ClasspathFilter, DefaultExcludes) map { (config, base, filter, excl) =>
			(base * (filter -- excl) +++ (base / config.name).descendentsExcept(filter, excl)).getFiles.toSeq
		}
	)
	def defaultPackageTasks: Seq[ScopedTask[_]] =
		for(task <- Seq(Package, PackageSrc, PackageDoc); conf <- Seq(CompileConf, TestConf)) yield (task in conf)

	val publishSettings: Seq[Project.Setting[_]] = Seq(
		PublishMavenStyle in GlobalScope :== true,
		PackageToPublish <<= defaultPackageTasks.dependOn,
		DeliverDepends <<= (PublishMavenStyle, MakePom.setting, PackageToPublish.setting) { (mavenStyle, makePom, ptp) =>
			if(mavenStyle) makePom.map(_ => ()) else ptp
		},
		MakePom <<= (IvyModule, MakePomConfig, PackageToPublish) map { (ivyModule, makePomConfig, _) => IvyActions.makePom(ivyModule, makePomConfig); makePomConfig.file },
		Deliver <<= deliver(PublishConfig),
		DeliverLocal <<= deliver(PublishLocalConfig),
		Publish <<= publish(PublishConfig, Deliver),
		PublishLocal <<= publish(PublishLocalConfig, DeliverLocal)
	)
	val baseSettings: Seq[Project.Setting[_]] = Seq(
		UnmanagedBase <<= Base / "lib",
		NormalizedName <<= Name(StringUtilities.normalize),
		Organization :== NormalizedName,
		ClasspathFilter in GlobalScope :== "*.jar",
		Resolvers <<= (ProjectResolver,BaseResolvers).map( (pr,rs) => pr +: Resolver.withDefaultResolvers(rs)),
		Offline in GlobalScope :== false,
		ModuleName :== NormalizedName,
		DefaultConfiguration in GlobalScope :== Some(Configurations.Compile),
		DefaultConfigurationMapping in GlobalScope <<= DefaultConfiguration{ case Some(d) => "*->" + d.name; case None => "*->*" },
		PathsIvy <<= Base(base => new IvyPaths(base, None)),
		OtherResolvers in GlobalScope :== Nil,
		ProjectResolver <<= projectResolver,
		ProjectDependencies <<= projectDependencies,
		LibraryDependencies in GlobalScope :== Nil,
		AllDependencies <<= concat(ProjectDependencies,LibraryDependencies),
		IvyLoggingLevel in GlobalScope :== UpdateLogging.Quiet,
		IvyXML in GlobalScope :== NodeSeq.Empty,
		IvyValidate in GlobalScope :== false,
		IvyScalaConfig in GlobalScope <<= ScalaVersion(v => Some(new IvyScala(v, Nil, false, false))),
		ModuleConfigurations in GlobalScope :== Nil,
		PublishTo in GlobalScope :== None,
		PomFile <<= (Target, Version, ModuleName)( (target, version, module) => target / (module + "-" + version + ".pom") ),
		PomArtifact <<= (PublishMavenStyle, ModuleName)( (mavenStyle, name) => if(mavenStyle) Artifact(name, "pom", "pom") :: Nil else Nil),
		Artifacts <<= (PomArtifact,ModuleName)( (pom,name) => Artifact(name) +: pom),
		ProjectID <<= (Organization,ModuleName,Version,Artifacts){ (org,module,version,as) => ModuleID(org, module, version).cross(true).artifacts(as : _*) },
		BaseResolvers in GlobalScope :== Nil,
		ProjectDescriptors <<= depMap,
		RetrievePattern :== "[type]/[organisation]/[module]/[artifact](-[revision])(-[classifier]).[ext]",
		UpdateConfig <<= (RetrieveConfig, IvyLoggingLevel)((conf,level) => new UpdateConfiguration(conf, level) ),
		RetrieveConfig :== None, //Some(new RetrieveConfiguration(managedDependencyPath asFile, retrievePattern, true))
		IvyConfig <<= (Resolvers, PathsIvy, OtherResolvers, ModuleConfigurations, Offline, AppConfig) map { (rs, paths, otherResolvers, moduleConfigurations, offline, app) =>
			// todo: pass logger from streams directly to IvyActions
			val lock = app.provider.scalaProvider.launcher.globalLock
			new InlineIvyConfiguration(paths, rs, otherResolvers, moduleConfigurations, offline, Some(lock), ConsoleLogger())
		},
		ModuleSettingsTask <<= (ProjectID, AllDependencies, IvyXML, ThisProject, DefaultConfiguration, IvyScalaConfig, IvyValidate) map {
			(pid, deps, ivyXML, project, defaultConf, ivyScala, validate) => new InlineConfiguration(pid, deps, ivyXML, project.configurations, defaultConf, ivyScala, validate)
		},
		MakePomConfig <<= PomFile(pomFile => makePomConfiguration(pomFile)),
		PublishConfig <<= (Target, PublishTo, IvyLoggingLevel) map { (outputDirectory, publishTo, level) => publishConfiguration( publishPatterns(outputDirectory), resolverName = getPublishTo(publishTo).name, logging = level) },
		PublishLocalConfig <<= (Target, IvyLoggingLevel) map { (outputDirectory, level) => publishConfiguration( publishPatterns(outputDirectory, true), logging = level ) },
		IvySbtTask <<= IvyConfig map { conf => new IvySbt(conf) },
		IvyModule <<= (IvySbtTask, ModuleSettingsTask) map { (ivySbt, settings) => new ivySbt.Module(settings) },
		Update <<= (IvyModule, UpdateConfig, CacheDirectory, streams) map { (ivyModule, updateConfig, cacheDirectory, s) =>
			cachedUpdate(cacheDirectory / "update", ivyModule, updateConfig, s.log)
		}
	)


	def deliver(config: TaskKey[PublishConfiguration]): Initialize[Task[Unit]] =
		(IvyModule, config, DeliverDepends) map { (ivyModule, config, _) => IvyActions.deliver(ivyModule, config) }
	def publish(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]): Initialize[Task[Unit]] =
		(IvyModule, config, deliverKey) map { (ivyModule, config, _) => IvyActions.publish(ivyModule, config) }

		import Cache._
		import CacheIvy.{classpathFormat, publishIC, updateIC}

	def cachedUpdate(cacheFile: File, module: IvySbt#Module, config: UpdateConfiguration, log: Logger): Map[String, Seq[File]] =
	{
		implicit val updateCache = updateIC
		val work = (_:  IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil) match { case conf :+: settings :+: config :+: HNil =>
			log.info("Updating...")
			val r = IvyActions.update(module, config)
			log.info("Done updating.")
			r
		}
		val f = cached(cacheFile)(work)
		f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)
	}
/*
	// can't cache deliver/publish easily since files involved are hidden behind patterns.  publish will be difficult to verify target-side anyway
	def cachedPublish(cacheFile: File)(g: (IvySbt#Module, PublishConfiguration) => Unit, module: IvySbt#Module, config: PublishConfiguration) => Unit =
	{ case module :+: config :+: HNil =>
	/*	implicit val publishCache = publishIC
		val f = cached(cacheFile) { (conf: IvyConfiguration, settings: ModuleSettings, config: PublishConfiguration) =>*/
		    g(module, config)
		/*}
		f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)*/
	}*/
	def makePomConfiguration(file: File, configurations: Option[Iterable[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true) = 
		new MakePomConfiguration(file, configurations, extra, process, filterRepositories)

	def getPublishTo(repo: Option[Resolver]): Resolver = repo getOrElse error("Repository for publishing is not specified.")

	def publishConfiguration(patterns: PublishPatterns, resolverName: String = "local", status: String = "release", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
	    new PublishConfiguration(patterns, status, resolverName, None, logging)

	def publishPatterns(outputPath: Path, publishIvy: Boolean = false): PublishPatterns =
	{
		val deliverPattern = (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath
		val srcArtifactPatterns: Seq[String] =
		{
			val pathPatterns =
				(outputPath / "[artifact]-[revision]-[type](-[classifier]).[ext]") ::
				(outputPath / "[artifact]-[revision](-[classifier]).[ext]") ::
				Nil
			pathPatterns.map(_.absolutePath)
		}
		new PublishPatterns( if(publishIvy) Some(deliverPattern) else None, srcArtifactPatterns)
	}

	def projectDependencies =
		(ThisProject, Data) map { (p, data) =>
			p.dependencies flatMap { dep => (ProjectID in dep.project) get data map { _.copy(configurations = dep.configuration) } }
		}

	def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
		(ThisProject, ThisProjectRef, Data) flatMap { (root, rootRef, data) =>
			val dependencies = (p: (ProjectRef, Project)) => p._2.dependencies.flatMap(pr => ThisProject in pr.project get data map { (pr.project, _) })
			depMap(Dag.topologicalSort((rootRef,root))(dependencies).dropRight(1), data)
		}

	def depMap(projects: Seq[(ProjectRef,Project)], data: Settings[Scope]): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		projects.flatMap { case (p,_) => IvyModule in p get data }.join.map { mods =>
			(mods.map{ mod =>
				val md = mod.moduleDescriptor
				(md.getModuleRevisionId, md)
			}).toMap
		}

	def projectResolver: Initialize[Task[Resolver]] =
		ProjectDescriptors map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	def analyzed[T](data: T, analysis: Analysis) = Attributed.blank(data).put(Command.Analysis, analysis)
	def makeProducts: Initialize[Task[Classpath]] =
		(CompileTask, CompileInputs) map { (analysis, i) => analyzed(i.config.classesDirectory, analysis) :: Nil }

	def internalDependencies: Initialize[Task[Classpath]] =
		(ThisProjectRef, ThisProject, Config, Data) flatMap internalDependencies0

	def internalDependencies0(projectRef: ProjectRef, project: Project, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
	{
			import java.util.LinkedHashSet
			import collection.JavaConversions.asScalaSet
		val visited = asScalaSet(new LinkedHashSet[(ProjectRef,String)])
		def visit(p: ProjectRef, project: Project, c: Configuration)
		{
			val applicableConfigs = allConfigs(c)
			for(ac <- applicableConfigs) // add all configurations in this project
				visited add (p, ac.name)
			val masterConfs = configurationNames(project)

			for( Project.ClasspathDependency(dep, confMapping) <- project.dependencies)
			{
				val depProject = ThisProject in dep get data getOrElse error("Invalid project: " + dep)
				val mapping = mapped(confMapping, masterConfs, configurationNames(depProject), "compile", "*->compile")
				// map master configuration 'c' and all extended configurations to the appropriate dependency configuration
				for(ac <- applicableConfigs; depConfName <- mapping(ac.name))
				{
					val depConf = configuration(dep, depProject, depConfName)
					if( ! visited( (dep, depConfName) ) )
						visit(dep, depProject, depConf)
				}
			}
		}
		visit(projectRef, project, conf)

		val productsTasks = asScalaSet(new LinkedHashSet[Task[Classpath]])
		for( (dep, c) <- visited )
			if( (dep != projectRef) || conf.name != c )
				productsTasks += products(dep, c, data)

		(productsTasks.toSeq.join).map(_.flatten)
	}
	
	def mapped(confString: Option[String], masterConfs: Seq[String], depConfs: Seq[String], default: String, defaultMapping: String): String => Seq[String] =
	{
		lazy val defaultMap = parseMapping(defaultMapping, masterConfs, depConfs, _ :: Nil)
		parseMapping(confString getOrElse default, masterConfs, depConfs, defaultMap)
	}
	def parseMapping(confString: String, masterConfs: Seq[String], depConfs: Seq[String], default: String => Seq[String]): String => Seq[String] =
		union(confString.split(";") map parseSingleMapping(masterConfs, depConfs, default))
	def parseSingleMapping( masterConfs: Seq[String], depConfs: Seq[String], default: String => Seq[String])(confString: String): String => Seq[String] =
	{
		val ms: Seq[(String,Seq[String])] =
			trim(confString.split("->",2)) match {
				case x :: Nil => for(a <- parseList(x, masterConfs)) yield (a,default(a))
				case x :: y :: Nil => val target = parseList(y, depConfs); for(a <- parseList(x, masterConfs)) yield (a,target)
				case _ => error("Invalid configuration '" + confString + "'") // shouldn't get here
			}
		val m = ms.toMap
		s => m.getOrElse(s, Nil)
	}

	def union[A,B](maps: Seq[A => Seq[B]]): A => Seq[B] =
		a => (Seq[B]() /: maps) { _ ++ _(a) } distinct;

	def configurationNames(p: Project): Seq[String] = p.configurations.map( _.name)
	def parseList(s: String, allConfs: Seq[String]): Seq[String] = (trim(s split ",") flatMap replaceWildcard(allConfs)).distinct
	def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] =
		if(conf == "") Nil else if(conf == "*") allConfs else conf :: Nil
	
	private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)
	def missingConfiguration(in: String, conf: String) =
		error("Configuration '" + conf + "' not defined in '" + in + "'")
	def allConfigs(conf: Configuration): Seq[Configuration] =
		Dag.topologicalSort(conf)(_.extendsConfigs)

	def configuration(ref: ProjectRef, dep: Project, conf: String): Configuration =
		dep.configurations.find(_.name == conf) getOrElse missingConfiguration(Project display ref, conf)
	def products(dep: ProjectRef, conf: String, data: Settings[Scope]): Task[Classpath] =
		Products in (dep, ConfigKey(conf)) get data getOrElse const(Nil)
	def defaultConfiguration(p: ProjectRef, data: Settings[Scope]): Configuration =
		flatten(DefaultConfiguration in p get data) getOrElse Configurations.Default
	def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap identity
}
