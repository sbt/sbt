/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import Build.data
	import Scope.{GlobalScope, ThisScope}
	import Project.{inConfig, Initialize, inScope, inTask, ScopedKey, Setting}
	import Configurations.{Compile => CompileConf, Test => TestConf}
	import EvaluateTask.{resolvedScoped, streams}
	import complete._
	import std.TaskExtra._
	import scala.xml.{Node => XNode,NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import Types._

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

	def extractAnalysis[T](a: Attributed[T]): (T, inc.Analysis) = 
		(a.data, a.metadata get Keys.Analysis getOrElse inc.Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, inc.Analysis] =
		(cp map extractAnalysis).toMap

	def buildCore: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
		JavaHome :== None,
		OutputStrategy :== None,
		Fork :== false,
		JavaOptions :== Nil,
		CrossPaths :== true,
		ShellPrompt :== (_ => "> "),
		Aggregate :== Aggregation.Enabled,
		MaxErrors :== 100,
		Commands :== Nil,
		Data <<= EvaluateTask.state map { state => Project.structure(state).data }
	))
	def projectCore: Seq[Setting[_]] = Seq(
		Name <<= ThisProject(_.id),
		Version :== "0.1"	
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
	
	def webPaths = Seq(
		WebappDir <<= Source / "webapp"
	)

	def compileBase = Seq(
		Compilers <<= (ScalaInstance, AppConfig, streams) map { (si, app, s) => Compile.compilers(si)(app, s.log) },
		JavacOptions in GlobalScope :== Nil,
		ScalacOptions in GlobalScope :== Nil,
		ScalaInstance <<= (AppConfig, ScalaVersion){ (app, version) => sbt.ScalaInstance(version, app.provider.scalaProvider.launcher) },
		ScalaVersion <<= AppConfig( _.provider.scalaProvider.version),
		Target <<= (Target, ScalaInstance, CrossPaths)( (t,si,cross) => if(cross) t / ("scala-" + si.actualVersion) else t )
	)

	lazy val configTasks = Seq(
		InitialCommands in GlobalScope :== "",
		CompileTask <<= compileTask,
		CompileInputs <<= compileInputsTask,
		ConsoleTask <<= console,
		ConsoleQuick <<= consoleQuick,
		DiscoveredMainClasses <<= CompileTask map discoverMainClasses,
		inTask(RunTask)(runner :: Nil).head,
		SelectMainClass <<= DiscoveredMainClasses map selectRunMain,
		MainClass in RunTask :== SelectMainClass,
		MainClass <<= DiscoveredMainClasses map selectPackageMain,
		RunTask <<= runTask(FullClasspath, MainClass in RunTask, Runner in RunTask),
		ScaladocOptions <<= ScalacOptions(identity),
		DocTask <<= docTask,
		CopyResources <<= copyResources
	)

	lazy val projectTasks: Seq[Setting[_]] = Seq(
		CleanFiles <<= (Target, SourceManaged) { _ :: _ :: Nil },
		Clean <<= CleanFiles map IO.delete,
		ConsoleProject <<= consoleProject
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
			val tests = Test.discover(frameworkMap.values.toSeq, analysis, s.log)._1
			IO.writeLines(s.text(CompletionsID), tests.map(_.name).distinct)
			tests
		},
		TestListeners <<= (streams in TestTask) map ( s => TestLogger(s.log) :: Nil ),
		TestOptions <<= TestListeners map { listeners => Test.Listeners(listeners) :: Nil },
		ExecuteTests <<= (streams in TestTask, LoadedTestFrameworks, TestOptions, TestLoader, DefinedTests) flatMap {
			(s, frameworkMap, options, loader, discovered) => Test(frameworkMap, loader, discovered, options, s.log)
		},
		TestTask <<= (ExecuteTests, streams) map { (results, s) => Test.showResults(s.log, results) },
		TestOnly <<= testOnly
	)

	def testOnly = 
	InputTask(resolvedScoped(testOnlyParser)) ( result =>  
		(streams, LoadedTestFrameworks, TestOptions, TestLoader, DefinedTests, result) flatMap {
			case (s, frameworks, opts, loader, discovered, (tests, frameworkOptions)) =>
				val modifiedOpts = Test.Filter(if(tests.isEmpty) _ => true else tests.toSet ) +: Test.Argument(frameworkOptions : _*) +: opts
				Test(frameworks, loader, discovered, modifiedOpts, s.log) map { results =>
					Test.showResults(s.log, results)
				}
		}
	)
	
	lazy val packageBase = Seq(
		jarName,
		PackageOptions in GlobalScope :== Nil,
		NameToString in GlobalScope :== (ArtifactName.show _)
	)
	lazy val packageConfig = Seq(
		JarName <<= (JarName, Config) { (n,c) => n.copy(config = c.name) },
		PackageOptions in Package <<= (PackageOptions, MainClass in Package) map { _ ++ _.map(sbt.Package.MainClass.apply).toList }
	) ++
	packageTasks(Package, "", packageBin) ++
	packageTasks(PackageSrc, "src", packageSrc) ++
	packageTasks(PackageDoc, "doc", packageDoc)

	private[this] val allSubpaths = (_: File).###.***.xx.toSeq

	def packageBin = concat(classMappings, resourceMappings)
	def packageDoc = DocTask map allSubpaths
	def packageSrc = concat(resourceMappings, sourceMappings)

	private type Mappings = Initialize[Task[Seq[(File, String)]]]
	def concat(as: Mappings, bs: Mappings) = (as zipWith bs)( (a,b) => (a :^: b :^: KNil) map { case a :+: b :+: HNil => a ++ b } )
	def classMappings = (CompileInputs, CompileTask) map { (in, _) => allSubpaths(in.config.classesDirectory) }
	// drop base directories, since there are no valid mappings for these
	def sourceMappings = (Sources, SourceDirectories, Base) map { (srcs, sdirs, base) =>
		 ( (srcs --- sdirs --- base) x (relativeTo(sdirs)|relativeTo(base))) toSeq
	}
	def resourceMappings = (Resources, ResourceDirectories) map { (rs, rdirs) =>
		(rs --- rdirs) x relativeTo(rdirs) toSeq
	}
	
	def jarName  =  JarName <<= (ModuleName, Version, ScalaVersion, CrossPaths) { (n,v, sv, withCross) =>
		ArtifactName(base = n, version = v, config = "", tpe = "", ext = "jar", cross = if(withCross) sv else "")
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

	def runTask(classpath: ScopedTask[Classpath], mainClass: ScopedTask[Option[String]], run: ScopedSetting[ScalaRun]): Initialize[InputTask[Unit]] =
		InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
			(classpath, mainClass, run, streams, result) map { (cp, main, runner, s, args) =>
				val mainClass = main getOrElse error("No main class detected.")
				runner.run(mainClass, data(cp), args, s.log) foreach error
			}
		}

	def runner =
		Runner <<= (ScalaInstance, Base, JavaOptions, OutputStrategy, Fork, JavaHome) { (si, base, options, strategy, fork, javaHome) =>
			if(fork) {
				new ForkRun( ForkOptions(scalaJars = si.jars, javaHome = javaHome, outputStrategy = strategy,
					runJVMOptions = options, workingDirectory = Some(base)) )
			} else
				new Run(si)
		}

	def docTask: Initialize[Task[File]] =
		(CompileInputs, streams, DocDirectory, Config, ScaladocOptions) map { (in, s, target, config, options) =>
			val d = new Scaladoc(in.config.maxErrors, in.compilers.scalac)
			d(nameForSrc(config.name), in.config.sources, in.config.classpath, target, options)(s.log)
			target
		}

	def mainRunTask = RunTask <<= runTask(FullClasspath in Configurations.Runtime, MainClass in RunTask, Runner in RunTask)

	def discoverMainClasses(analysis: inc.Analysis): Seq[String] =
		compile.Discovery.applications(Test.allDefs(analysis)) collect { case (definition, discovered) if(discovered.hasMain) => definition.name }

	def consoleProject = (EvaluateTask.state, streams, InitialCommands in ConsoleProject) map { (state, s, extra) => Console.sbt(state, extra)(s.log) }
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
		val mappings = (resources --- dirs) x rebase(dirs, target)
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

	lazy val defaultWebPaths = inConfig(CompileConf)(webPaths)
	
	def noAggregation = Seq(RunTask, ConsoleTask, ConsoleQuick)
	lazy val disableAggregation = noAggregation map disableAggregate
	def disableAggregate(k: Scoped) =
		Aggregate in Scope.GlobalScope.copy(task = Select(k.key)) :== false
	
	lazy val baseTasks: Seq[Setting[_]] = projectTasks ++ packageBase

	lazy val defaultWebTasks = Nil

	lazy val baseClasspaths = Classpaths.publishSettings ++ Classpaths.baseSettings
	lazy val configSettings = Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig

	lazy val compileSettings = configSettings ++ (mainRunTask +: addBaseSources)
	lazy val testSettings = configSettings ++ testTasks

	lazy val itSettings = inConfig(Configurations.IntegrationTest)(testSettings)
	lazy val defaultConfigs = inConfig(CompileConf)(compileSettings) ++ inConfig(TestConf)(testSettings)

	lazy val defaultSettings: Seq[Setting[_]] = projectCore ++ paths ++ baseClasspaths ++ baseTasks ++ compileBase ++ defaultConfigs ++ disableAggregation
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

	lazy val configSettings: Seq[Setting[_]] = Seq(
		ExternalDependencyClasspath <<= concat(UnmanagedClasspath, ManagedClasspath),
		DependencyClasspath <<= concat(InternalDependencyClasspath, ExternalDependencyClasspath),
		FullClasspath <<= concat(Products, DependencyClasspath),
		InternalDependencyClasspath <<= internalDependencies,
		UnmanagedClasspath <<= unmanagedDependencies,
		Products <<= makeProducts,
		ManagedClasspath <<= (Config, Update) map { (config, up) => up.getOrElse(config.name, error("Configuration '" + config.name + "' unresolved by 'update'.")) },
		UnmanagedJars <<= (Config, UnmanagedBase, ClasspathFilter, DefaultExcludes) map { (config, base, filter, excl) =>
			(base * (filter -- excl) +++ (base / config.name).descendentsExcept(filter, excl)).getFiles.toSeq
		}
	)
	def defaultPackageTasks: Seq[ScopedTask[_]] =
		for(task <- Seq(Package, PackageSrc, PackageDoc); conf <- Seq(CompileConf, TestConf)) yield (task in conf)

	val publishSettings: Seq[Setting[_]] = Seq(
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
	val baseSettings: Seq[Setting[_]] = Seq(
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
		PomName <<= (ModuleName, Version, ScalaVersion, CrossPaths) { (n,v,sv, withCross) =>
			ArtifactName(base = n, version = v, config = "", tpe = "", ext = "pom", cross = if(withCross) sv else "")
		},
		PomFile <<= (Target, PomName, NameToString) { (t, n, toString) => t / toString(n) },
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

	def analyzed[T](data: T, analysis: inc.Analysis) = Attributed.blank(data).put(Keys.Analysis, analysis)
	def makeProducts: Initialize[Task[Classpath]] =
		(CompileTask, CompileInputs, CopyResources) map { (analysis, i, _) => analyzed(i.config.classesDirectory, analysis) :: Nil }

	def internalDependencies: Initialize[Task[Classpath]] =
		(ThisProjectRef, ThisProject, Config, Data) flatMap internalDependencies0
	def unmanagedDependencies: Initialize[Task[Classpath]] =
		(ThisProjectRef, ThisProject, Config, Data) flatMap unmanagedDependencies0

		import java.util.LinkedHashSet
		import collection.JavaConversions.asScalaSet
	def interSort(projectRef: ProjectRef, project: Project, conf: Configuration, data: Settings[Scope]): Seq[(ProjectRef,String)] =
	{
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
		visited.toSeq
	}
	def unmanagedDependencies0(projectRef: ProjectRef, project: Project, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, data, true, unmanagedLibs)
	def internalDependencies0(projectRef: ProjectRef, project: Project, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, data, false, products)
	def interDependencies(projectRef: ProjectRef, project: Project, conf: Configuration, data: Settings[Scope], includeSelf: Boolean,
		f: (ProjectRef, String, Settings[Scope]) => Task[Classpath]): Task[Classpath] =
	{
		val visited = interSort(projectRef, project, conf, data)
		val tasks = asScalaSet(new LinkedHashSet[Task[Classpath]])
		for( (dep, c) <- visited )
			if(includeSelf ||  (dep != projectRef) || conf.name != c )
				tasks += f(dep, c, data)

		(tasks.toSeq.join).map(_.flatten.distinct)
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
		getClasspath(Products, dep, conf, data)
	def unmanagedLibs(dep: ProjectRef, conf: String, data: Settings[Scope]): Task[Classpath] =
		getClasspath(UnmanagedJars, dep, conf, data)
	def getClasspath(key: TaskKey[Classpath], dep: ProjectRef, conf: String, data: Settings[Scope]): Task[Classpath] =
		( key in (dep, ConfigKey(conf)) ) get data getOrElse const(Nil)
	def defaultConfiguration(p: ProjectRef, data: Settings[Scope]): Configuration =
		flatten(DefaultConfiguration in p get data) getOrElse Configurations.Default
	def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap identity
}
