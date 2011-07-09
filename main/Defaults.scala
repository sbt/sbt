/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Build.data
	import Scope.{fillTaskAxis, GlobalScope, ThisScope}
	import compiler.Discovery
	import Project.{inConfig, Initialize, inScope, inTask, ScopedKey, Setting, SettingsDefinition}
	import Artifact.{DocClassifier, SourceClassifier}
	import Configurations.{Compile, CompilerPlugin, IntegrationTest, names, Provided, Runtime, Test}
	import complete._
	import std.TaskExtra._
	import inc.{FileValueCache, Locate}
	import org.scalatools.testing.{AnnotatedFingerprint, SubclassFingerprint}

	import scala.xml.{Node => XNode,NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import java.io.File
	import java.net.URL
	import java.util.concurrent.Callable
	import sbinary.DefaultProtocol.StringFormat
	import Cache.seqFormat

	import Types._
	import Path._
	import Keys._

object Defaults extends BuildCommon
{
	def configSrcSub(key: ScopedSetting[File]): Initialize[File] = (key in ThisScope.copy(config = Global), configuration) { (src, conf) => src / nameForSrc(conf.name) }
	def nameForSrc(config: String) = if(config == "compile") "main" else config
	def prefix(config: String) = if(config == "compile") "" else config + "-"

	def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = app.provider.scalaProvider.launcher.globalLock

	def extractAnalysis[T](a: Attributed[T]): (T, inc.Analysis) = 
		(a.data, a.metadata get Keys.analysis getOrElse inc.Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, inc.Analysis] =
		(cp map extractAnalysis).toMap

	def buildCore: Seq[Setting[_]] = thisBuildCore ++ globalCore
	def thisBuildCore: Seq[Setting[_]] = inScope(GlobalScope.copy(project = Select(ThisBuild)))(Seq(
		managedDirectory <<= baseDirectory(_ / "lib_managed")
	))
	def globalCore: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
		parallelExecution :== true,
		sbtVersion in GlobalScope <<= appConfiguration { _.provider.id.version },
		pollInterval :== 500,
		logBuffered :== false,
		autoScalaLibrary :== true,
		definesClass := FileValueCache(Locate.definesClass _ ).get,
		trapExit :== false,
		trapExit in run :== true,
		logBuffered in testOnly :== true,
		logBuffered in test :== true,
		traceLevel in console :== Int.MaxValue,
		traceLevel in consoleProject :== Int.MaxValue,
		autoCompilerPlugins :== true,
		internalConfigurationMap :== Configurations.internalMap _,
		initialize :== (),
		credentials :== Nil,
		scalaHome :== None,
		javaHome :== None,
		extraLoggers :== { _ => Nil },
		version :== "0.1",
		outputStrategy :== None,
		exportJars :== false,
		fork :== false,
		javaOptions :== Nil,
		sbtPlugin :== false,
		crossPaths :== true,
		classpathTypes := Set("jar", "bundle"),
		aggregate :== Aggregation.Enabled,
		maxErrors :== 100,
		showTiming :== true,
		timingFormat :== Aggregation.defaultFormat,
		showSuccess :== true,
		commands :== Nil,
		retrieveManaged :== false,
		buildStructure <<= state map Project.structure,
		settings <<= buildStructure map ( _.data ),
		artifactClassifier :== None,
		artifactClassifier in packageSrc :== Some(SourceClassifier),
		artifactClassifier in packageDoc :== Some(DocClassifier),
		checksums :== IvySbt.DefaultChecksums,
		pomExtra :== NodeSeq.Empty,
		pomPostProcess :== idFun,
		pomAllRepositories :== false,
		pomIncludeRepository :== Classpaths.defaultRepositoryFilter
	))
	def projectCore: Seq[Setting[_]] = Seq(
		name <<= thisProject(_.id),
		logManager <<= extraLoggers(LogManager.defaults),
		runnerSetting
	)
	def paths = Seq(
		baseDirectory <<= thisProject(_.base),
		target <<= baseDirectory / "target",
		defaultExcludes in GlobalScope :== (".*"  - ".") || HiddenFileFilter,
		historyPath <<= target(t => Some(t / ".history")),
		sourceDirectory <<= baseDirectory / "src",
		sourceFilter in GlobalScope :== ("*.java" | "*.scala"),
		sourceManaged <<= crossTarget / "src_managed",
		resourceManaged <<= crossTarget / "resource_managed",
		cacheDirectory <<= target / "cache"
	)

	lazy val configPaths = sourceConfigPaths ++ resourceConfigPaths ++ outputConfigPaths
	lazy val sourceConfigPaths = Seq(
		sourceDirectory <<= configSrcSub( sourceDirectory),
		sourceManaged <<= configSrcSub(sourceManaged),
		scalaSource <<= sourceDirectory / "scala",
		javaSource <<= sourceDirectory / "java",
		unmanagedSourceDirectories <<= Seq(scalaSource, javaSource).join,
		unmanagedSources <<= collectFiles(unmanagedSourceDirectories, sourceFilter, defaultExcludes in unmanagedSources),
		managedSourceDirectories <<= Seq(sourceManaged).join,
		managedSources <<= generate(sourceGenerators),
		sourceGenerators :== Nil,
		sourceDirectories <<= Classpaths.concatSettings(unmanagedSourceDirectories, managedSourceDirectories),
		sources <<= Classpaths.concat(unmanagedSources, managedSources)
	)
	lazy val resourceConfigPaths = Seq(
		resourceDirectory <<= sourceDirectory / "resources",
		resourceManaged <<= configSrcSub(resourceManaged),
		unmanagedResourceDirectories <<= Seq(resourceDirectory).join,
		managedResourceDirectories <<= Seq(resourceManaged).join,
		resourceDirectories <<= Classpaths.concatSettings(unmanagedResourceDirectories, managedResourceDirectories),
		unmanagedResources <<= (unmanagedResourceDirectories, defaultExcludes in unmanagedResources) map unmanagedResourcesTask,
		resourceGenerators :== Nil,
		resourceGenerators <+= (definedSbtPlugins, resourceManaged) map writePluginsDescriptor,
		managedResources <<= generate(resourceGenerators),
		resources <<= Classpaths.concat(managedResources, unmanagedResources)
	)
	lazy val outputConfigPaths = Seq(
		cacheDirectory <<= (cacheDirectory, configuration) { _ / _.name },
		classDirectory <<= (crossTarget, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "classes") },
		docDirectory <<= (crossTarget, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "api") }
	)
	def addBaseSources = Seq(
		unmanagedSources <<= (unmanagedSources, baseDirectory, sourceFilter, defaultExcludes in unmanagedSources) map {
			(srcs,b,f,excl) => (srcs +++ b * (f -- excl)).get 
		}
	)

	def compileBase = inTask(console)(compilersSetting :: Nil) ++ Seq(
		classpathOptions in GlobalScope :== ClasspathOptions.boot,
		classpathOptions in GlobalScope in console :== ClasspathOptions.repl,
		compileOrder in GlobalScope :== CompileOrder.Mixed,
		compilersSetting,
		javacOptions in GlobalScope :== Nil,
		scalacOptions in GlobalScope :== Nil,
		scalaInstance <<= scalaInstanceSetting,
		scalaVersion in GlobalScope <<= appConfiguration( _.provider.scalaProvider.version),
		crossScalaVersions in GlobalScope <<= Seq(scalaVersion).join,
		crossTarget <<= (target, scalaInstance, crossPaths)( (t,si,cross) => if(cross) t / ("scala-" + si.actualVersion) else t ),
		cacheDirectory <<= crossTarget / "cache"
	)
	def compilersSetting = compilers <<= (scalaInstance, appConfiguration, streams, classpathOptions, javaHome) map { (si, app, s, co, jh) => Compiler.compilers(si, co, jh)(app, s.log) }

	lazy val configTasks = Seq(
		initialCommands in GlobalScope :== "",
		compile <<= compileTask,
		compileInputs <<= compileInputsTask,
		compileIncSetup <<= compileIncSetupTask,
		console <<= consoleTask,
		consoleQuick <<= consoleQuickTask,
		discoveredMainClasses <<= TaskData.write(compile map discoverMainClasses) triggeredBy compile,
		definedSbtPlugins <<= discoverPlugins,
		inTask(run)(runnerSetting :: Nil).head,
		selectMainClass <<= discoveredMainClasses map selectRunMain,
		mainClass in run <<= (selectMainClass in run).identity,
		mainClass <<= discoveredMainClasses map selectPackageMain,
		run <<= runTask(fullClasspath, mainClass in run, runner in run),
		runMain <<= runMainTask(fullClasspath, runner in run),
		scaladocOptions <<= scalacOptions.identity,
		doc <<= docTask,
		copyResources <<= copyResourcesTask
	)

	lazy val projectTasks: Seq[Setting[_]] = Seq(
		cleanFiles <<= Seq(managedDirectory, target).join,
		cleanKeepFiles <<= historyPath(_.toList),
		clean <<= (cleanFiles, cleanKeepFiles) map doClean,
		consoleProject <<= consoleProjectTask,
		watchSources <<= watchSourcesTask,
		watchTransitiveSources <<= watchTransitiveSourcesTask,
		watch <<= watchSetting
	)

	def generate(generators: ScopedSetting[Seq[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] = generators {_.join.map(_.flatten) }

	def inAllConfigurations[T](key: ScopedTask[T]): Initialize[Task[Seq[T]]] = (state, thisProjectRef) flatMap { (state, ref) =>
		val structure = Project structure state
		val configurations = Project.getProject(ref, structure).toList.flatMap(_.configurations)
		configurations.flatMap { conf =>
			key in (ref, conf) get structure.data
		} join
	}
	def watchTransitiveSourcesTask: Initialize[Task[Seq[File]]] =
		(state, thisProjectRef) flatMap { (s, base) =>
			inAllDependencies(base, watchSources.task, Project structure s).join.map(_.flatten)
		}
	def watchSourcesTask: Initialize[Task[Seq[File]]] =
		Seq(unmanagedSources, unmanagedResources).map(inAllConfigurations).join { _.join.map(_.flatten.flatten.distinct) }

	def watchSetting: Initialize[Watched] = (pollInterval, thisProjectRef) { (interval, base) =>
		new Watched {
			val scoped = watchTransitiveSources in base
			val key = ScopedKey(scoped.scope, scoped.key)
			override def pollInterval = interval
			override def watchPaths(s: State) = EvaluateTask.evaluateTask(Project structure s, key, s, base) match {
				case Some(Value(ps)) => ps
				case Some(Inc(i)) => throw i
				case None => error("key not found: " + Project.display(key))
			}
		}
	}
	def scalaInstanceSetting = (appConfiguration, scalaVersion, scalaHome){ (app, version, home) =>
		val launcher = app.provider.scalaProvider.launcher
		home match {
			case None => ScalaInstance(version, launcher)
			case Some(h) => ScalaInstance(h, launcher)
		}
	}
	def unmanagedResourcesTask(dirs: Seq[File], excl: FileFilter) =
		dirs.descendentsExcept("*",excl).get

	lazy val testTasks: Seq[Setting[_]] = testTaskOptions(test) ++ testTaskOptions(testOnly) ++ Seq(	
		testLoader <<= (fullClasspath, scalaInstance) map { (cp, si) => TestFramework.createTestLoader(data(cp), si) },
		testFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs2, Specs, ScalaTest, ScalaCheckCompat, ScalaTestCompat, SpecsCompat, JUnit)
		},
		loadedTestFrameworks <<= (testFrameworks, streams, testLoader) map { (frameworks, s, loader) =>
			frameworks.flatMap(f => f.create(loader, s.log).map( x => (f,x)).toIterable).toMap
		},
		definedTests <<= TaskData.writeRelated(detectTests)(_.map(_.name).distinct) triggeredBy compile,
		testListeners in GlobalScope :== Nil,
		testOptions in GlobalScope :== Nil,
		executeTests <<= (streams in test, loadedTestFrameworks, parallelExecution in test, testOptions in test, testLoader, definedTests) flatMap {
			(s, frameworkMap, par, options, loader, discovered) => Tests(frameworkMap, loader, discovered, options, par, s.log)
		},
		test <<= (executeTests, streams) map { (results, s) => Tests.showResults(s.log, results) },
		testOnly <<= testOnlyTask
	)

	lazy val TaskGlobal: Scope = ThisScope.copy(task = Global)
	def testTaskOptions(key: Scoped): Seq[Setting[_]] = inTask(key)( Seq(
		testListeners <<= (streams, resolvedScoped, streamsManager, logBuffered, testListeners in TaskGlobal) map { (s, sco, sm, buff, ls) =>
			TestLogger(s.log, testLogger(sm, test in sco.scope), buff) +: ls
		},
		testOptions <<= (testOptions in TaskGlobal, testListeners) map { (options, ls) => Tests.Listeners(ls) +: options }
	) )
	def testLogger(manager: Streams, baseKey: Scoped)(tdef: TestDefinition): Logger =
	{
		val scope = baseKey.scope
		val extra = scope.extra match { case Select(x) => x; case _ => AttributeMap.empty }
		val key = ScopedKey(scope.copy(extra = Select(testExtra(extra, tdef))), baseKey.key)
		manager(key).log
	}
	def buffered(log: Logger): Logger = new BufferedLogger(FullLogger(log))
	def testExtra(extra: AttributeMap, tdef: TestDefinition): AttributeMap =
	{
		val mod = tdef.fingerprint match { case f: SubclassFingerprint => f.isModule; case f: AnnotatedFingerprint => f.isModule; case _ => false }
		extra.put(name.key, tdef.name).put(isModule, mod)
	}

	def testOnlyTask = 
	InputTask( TaskData(definedTests)(testOnlyParser)(Nil) ) { result =>  
		(streams, loadedTestFrameworks, parallelExecution in testOnly, testOptions in testOnly, testLoader, definedTests, result) flatMap {
			case (s, frameworks, par, opts, loader, discovered, (tests, frameworkOptions)) =>
				val filter = selectedFilter(tests)
				val modifiedOpts = Tests.Filter(filter) +: Tests.Argument(frameworkOptions : _*) +: opts
				Tests(frameworks, loader, discovered, modifiedOpts, par, s.log) map { results =>
					Tests.showResults(s.log, results)
				}
		}
	}
	def selectedFilter(args: Seq[String]): String => Boolean =
	{
		val filters = args map GlobFilter.apply
		s => filters.isEmpty || filters.exists { _ accept s }
	}
	def detectTests: Initialize[Task[Seq[TestDefinition]]] = (loadedTestFrameworks, compile, streams) map { (frameworkMap, analysis, s) =>
		Tests.discover(frameworkMap.values.toSeq, analysis, s.log)._1
	}

	lazy val packageBase: Seq[Setting[_]] = Seq(
		artifact <<= normalizedName(n => Artifact(n)),
		packageOptions in GlobalScope :== Nil,
		artifactName in GlobalScope :== ( Artifact.artifactName _ )
	)
	lazy val packageConfig: Seq[Setting[_]] = Seq(
		packageOptions in packageBin <<= (packageOptions, mainClass in packageBin) map { _ ++ _.map(Package.MainClass.apply).toList }
	) ++
	packageTasks(packageBin, packageBinTask) ++
	packageTasks(packageSrc, packageSrcTask) ++
	packageTasks(packageDoc, packageDocTask)

	private[this] val allSubpaths = (dir: File) => (dir.*** --- dir) x (relativeTo(dir)|flat)

	def packageBinTask = products map { ps => ps flatMap { p => allSubpaths(p) } }
	def packageDocTask = doc map allSubpaths
	def packageSrcTask = concatMappings(resourceMappings, sourceMappings)

	private type Mappings = Initialize[Task[Seq[(File, String)]]]
	def concatMappings(as: Mappings, bs: Mappings) = (as zipWith bs)( (a,b) => (a :^: b :^: KNil) map { case a :+: b :+: HNil => a ++ b } )

	// drop base directories, since there are no valid mappings for these
	def sourceMappings = (unmanagedSources, unmanagedSourceDirectories, baseDirectory) map { (srcs, sdirs, base) =>
		 ( (srcs --- sdirs --- base) x (relativeTo(sdirs)|relativeTo(base)|flat)) toSeq
	}
	def resourceMappings = relativeMappings(unmanagedResources, unmanagedResourceDirectories)
	def relativeMappings(files: ScopedTaskable[Seq[File]], dirs: ScopedTaskable[Seq[File]]): Initialize[Task[Seq[(File, String)]]] =
		(files, dirs) map { (rs, rdirs) =>
			(rs --- rdirs) x (relativeTo(rdirs)|flat) toSeq
		}
	
	def collectFiles(dirs: ScopedTaskable[Seq[File]], filter: ScopedTaskable[FileFilter], excludes: ScopedTaskable[FileFilter]): Initialize[Task[Seq[File]]] =
		(dirs, filter, excludes) map { (d,f,excl) => d.descendentsExcept(f,excl).get }

	def artifactPathSetting(art: ScopedSetting[Artifact])  =  (crossTarget, projectID, art, scalaVersion, artifactName) { (t, module, a, sv, toString) => t / toString(sv, module, a) asFile }

	def pairID[A,B] = (a: A, b: B) => (a,b)
	def packageTasks(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in TaskGlobal <<= packageTask,
			packageConfiguration <<= packageConfigurationTask,
			mappings <<= mappingsTask,
			packagedArtifact <<= (artifact, key) map pairID,
			artifact <<= (artifact, artifactClassifier, configuration) { (a,classifier,c) =>
				val cPart = if(c == Compile) Nil else c.name :: Nil
				val combined = cPart ++ classifier.toList
				if(combined.isEmpty) a.copy(classifier = None, configurations = c :: Nil) else {
					val classifier = combined mkString "-"
					val confs = if(a.configurations.isEmpty) Artifact.classifierConf(classifier) :: Nil else a.configurations
					a.copy(classifier = Some(classifier), `type` = Artifact.classifierType(classifier), configurations = confs)
				}
			},
			cacheDirectory <<= cacheDirectory / key.key.label,
			artifactPath <<= artifactPathSetting(artifact)
		))
	def packageTask: Initialize[Task[File]] =
		(packageConfiguration, cacheDirectory, streams) map { (config, cacheDir, s) =>
			Package(config, cacheDir, s.log)
			config.jar
		}
	def packageConfigurationTask: Initialize[Task[Package.Configuration]] =
		(mappings, artifactPath, packageOptions) map { (srcs, path, options) =>
			new Package.Configuration(srcs, path, options)
		}

	def selectRunMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(Some(SimpleReader readLine _), classes)
	def selectPackageMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(None, classes)

	def doClean(clean: Seq[File], preserve: Seq[File]): Unit =
		IO.withTemporaryDirectory { temp =>
			val mappings = preserve.filter(_.exists).zipWithIndex map { case (f, i) => (f, new File(temp, i.toHexString)) }
			IO.move(mappings)
			IO.delete(clean)
			IO.move(mappings.map(_.swap))
		}
	def runMainTask(classpath: ScopedTask[Classpath], scalaRun: ScopedSetting[ScalaRun]): Initialize[InputTask[Unit]] =
	{
			import DefaultParsers._
		InputTask( TaskData(discoveredMainClasses)(runMainParser)(Nil) ) { result =>  
			(classpath, scalaRun, streams, result) map { case (cp, runner, s, (mainClass, args)) =>
				toError(runner.run(mainClass, data(cp), args, s.log))
			}
		}
	}

	def runTask(classpath: ScopedTask[Classpath], mainClassTask: ScopedTask[Option[String]], scalaRun: ScopedSetting[ScalaRun]): Initialize[InputTask[Unit]] =
		inputTask { result =>
			(classpath, mainClassTask, scalaRun, streams, result) map { (cp, main, runner, s, args) =>
				val mainClass = main getOrElse error("No main class detected.")
				toError(runner.run(mainClass, data(cp), args, s.log))
			}
		}

	def runnerSetting = runner <<= runnerInit
	def runnerInit: Initialize[ScalaRun] = (scalaInstance, baseDirectory, javaOptions, outputStrategy, fork, javaHome, trapExit) { (si, base, options, strategy, forkRun, javaHomeDir, trap) =>
			if(forkRun) {
				new ForkRun( ForkOptions(scalaJars = si.jars, javaHome = javaHomeDir, outputStrategy = strategy,
					runJVMOptions = options, workingDirectory = Some(base)) )
			} else
				new Run(si, trap)
		}

	def docTask: Initialize[Task[File]] =
		(cacheDirectory, compileInputs, streams, docDirectory, configuration, scaladocOptions) map { (cache, in, s, target, config, options) =>
			val d = new Scaladoc(in.config.maxErrors, in.compilers.scalac)
			val cp = in.config.classpath.toList - in.config.classesDirectory
			d.cached(cache / "doc", nameForSrc(config.name), in.config.sources, cp, target, options, s.log)
			target
		}

	def mainRunTask = run <<= runTask(fullClasspath in Runtime, mainClass in run, runner in run)
	def mainRunMainTask = runMain <<= runMainTask(fullClasspath in Runtime, runner in run)

	def discoverMainClasses(analysis: inc.Analysis): Seq[String] =
		Discovery.applications(Tests.allDefs(analysis)) collect { case (definition, discovered) if(discovered.hasMain) => definition.name }

	def consoleProjectTask = (state, streams, initialCommands in consoleProject) map { (state, s, extra) => ConsoleProject(state, extra)(s.log); println() }
	def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
	def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
	def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] = (compilers in task, classpath, scalacOptions in task, initialCommands in task, streams) map {
		(cs, cp, options, initCommands, s) =>
			(new Console(cs.scalac))(data(cp), options, initCommands, s.log).foreach(msg => error(msg))
			println()
	}
	
	def compileTask = (compileInputs, streams) map { (i,s) => Compiler(i,s.log) }
	def compileIncSetupTask = 
		(dependencyClasspath, cacheDirectory, definesClass) map { (cp, cacheDir, definesC) =>
			Compiler.IncSetup(analysisMap(cp), definesC, cacheDir / "compile")
		}
	def compileInputsTask =
		(dependencyClasspath, sources, compilers, javacOptions, scalacOptions, classDirectory, compileOrder, compileIncSetup, streams) map {
		(cp, srcs, cs, javacOpts, scalacOpts, classes, order, incSetup, s) =>
			val classpath = classes +: data(cp)
			Compiler.inputs(classpath, srcs, classes, scalacOpts, javacOpts, 100, order)(cs, incSetup, s.log)
		}

	def writePluginsDescriptor(plugins: Set[String], dir: File): List[File] =
	{
		val descriptor: File = dir / "sbt" / "sbt.plugins"
		if(plugins.isEmpty)
		{
			IO.delete(descriptor)
			Nil
		}
		else
		{
			IO.writeLines(descriptor, plugins.toSeq.sorted)
			descriptor :: Nil
		}
	}
	def discoverPlugins: Initialize[Task[Set[String]]] = (compile, sbtPlugin, streams) map { (analysis, isPlugin, s) => if(isPlugin) discoverSbtPlugins(analysis, s.log) else Set.empty }
	def discoverSbtPlugins(analysis: inc.Analysis, log: Logger): Set[String] =
	{
		val pluginClass = classOf[Plugin].getName
		val discovery = Discovery(Set(pluginClass), Set.empty)( Tests allDefs analysis )
		discovery collect { case (df, disc) if (disc.baseClasses contains pluginClass) && disc.isModule => df.name } toSet;
	}

	def copyResourcesTask =
	(classDirectory, cacheDirectory, resources, resourceDirectories, streams) map { (target, cache, resrcs, dirs, s) =>
		val cacheFile = cache / "copy-resources"
		val mappings = (resrcs --- dirs) x (rebase(dirs, target) | flat(target))
		s.log.debug("Copy resource mappings: " + mappings.mkString("\n\t","\n\t",""))
		Sync(cacheFile)( mappings )
		mappings
	}

	def runMainParser: (State, Seq[String]) => Parser[(String, Seq[String])] =
	{
			import DefaultParsers._
		(state, mainClasses) => Space ~> token(NotSpace examples mainClasses.toSet) ~ spaceDelimited("<arg>")
	}

	def testOnlyParser: (State, Seq[String]) => Parser[(Seq[String],Seq[String])] =
	{ (state, tests) =>
			import DefaultParsers._
		val selectTests = distinctParser(tests.toSet, true)
		val options = (token(Space) ~> token("--") ~> spaceDelimited("<option>")) ?? Nil
		selectTests ~ options
	}

	def distinctParser(exs: Set[String], raw: Boolean): Parser[Seq[String]] =
	{
			import DefaultParsers._
		val base = token(Space) ~> token(NotSpace - "--" examples exs)
		val recurse = base flatMap { ex =>
			val (matching, notMatching) = exs.partition( GlobFilter(ex).accept _ )
			distinctParser(notMatching, raw) map { result => if(raw) ex +: result else matching.toSeq ++ result }
		}
		recurse ?? Nil
	}

	def inAllDependencies[T](base: ProjectRef, key: ScopedSetting[T], structure: Load.BuildStructure): Seq[T] =
	{
		def deps(ref: ProjectRef): Seq[ProjectRef] =
			Project.getProject(ref, structure).toList.flatMap { p =>
				p.dependencies.map(_.project) ++ p.aggregate
			}

		inAllDeps(base, deps, key, structure.data)
	}
	def inAllDeps[T](base: ProjectRef, deps: ProjectRef => Seq[ProjectRef], key: ScopedSetting[T], data: Settings[Scope]): Seq[T] =
		inAllProjects(Dag.topologicalSort(base)(deps), key, data)
	def inAllProjects[T](allProjects: Seq[Reference], key: ScopedSetting[T], data: Settings[Scope]): Seq[T] =
		allProjects.flatMap { p => key in p get data }

	val CompletionsID = "completions"
	
	def noAggregation = Seq(run, console, consoleQuick, consoleProject)
	lazy val disableAggregation = noAggregation map disableAggregate
	def disableAggregate(k: Scoped) =
		aggregate in Scope.GlobalScope.copy(task = Select(k.key)) :== false
	
	lazy val baseTasks: Seq[Setting[_]] = projectTasks ++ packageBase

	lazy val baseClasspaths: Seq[Setting[_]] = Classpaths.publishSettings ++ Classpaths.baseSettings
	lazy val configSettings: Seq[Setting[_]] = Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig ++ Classpaths.compilerPluginConfig

	lazy val compileSettings: Seq[Setting[_]] = configSettings ++ (mainRunMainTask +: mainRunTask +: addBaseSources)
	lazy val testSettings: Seq[Setting[_]] = configSettings ++ testTasks

	lazy val itSettings: Seq[Setting[_]] = inConfig(IntegrationTest)(testSettings)
	lazy val defaultConfigs: Seq[Setting[_]] = inConfig(Compile)(compileSettings) ++ inConfig(Test)(testSettings) ++ inConfig(Runtime)(Classpaths.configSettings)
		

	// settings that are not specific to a configuration
	lazy val projectBaseSettings: Seq[Setting[_]] = projectCore ++ paths ++ baseClasspaths ++ baseTasks ++ compileBase ++ disableAggregation
	lazy val defaultSettings: Seq[Setting[_]] = projectBaseSettings ++ defaultConfigs
}
object Classpaths
{
		import Path._
		import Keys._
		import Scope.ThisScope
		import Defaults._
		import Attributed.{blank, blankSeq}

	def concatDistinct[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] = (a,b) map { (x,y) => (x ++ y).distinct }
	def concat[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] = (a,b) map ( _ ++ _)
	def concatSettings[T](a: ScopedSetting[Seq[T]], b: ScopedSetting[Seq[T]]): Initialize[Seq[T]] = (a,b)(_ ++ _)

	lazy val configSettings: Seq[Setting[_]] = Seq(
		externalDependencyClasspath <<= concat(unmanagedClasspath, managedClasspath),
		dependencyClasspath <<= concat(internalDependencyClasspath, externalDependencyClasspath),
		fullClasspath <<= concatDistinct(exportedProducts, dependencyClasspath),
		internalDependencyClasspath <<= internalDependencies,
		unmanagedClasspath <<= unmanagedDependencies,
		products <<= makeProducts,
		productDirectories <<= compileInputs map (_.config.classesDirectory :: Nil),
		exportedProducts <<= exportProductsTask,
		classpathConfiguration <<= (internalConfigurationMap, configuration)( _ apply _ ),
		managedClasspath <<= (classpathConfiguration, classpathTypes, update) map managedJars,
		unmanagedJars <<= (configuration, unmanagedBase, classpathFilter, defaultExcludes in unmanagedJars) map { (config, base, filter, excl) =>
			(base * (filter -- excl) +++ (base / config.name).descendentsExcept(filter, excl)).classpath
		}
	)
	def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
	lazy val defaultPackages: Seq[ScopedTask[File]] =
		for(task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
	lazy val defaultArtifactTasks: Seq[ScopedTask[File]] = makePom +: defaultPackages

	def packaged(pkgTasks: Seq[ScopedTask[File]]): Initialize[Task[Map[Artifact, File]]] =
		enabledOnly(packagedArtifact.task, pkgTasks).map(_.join.map(_.toMap))
	def artifactDefs(pkgTasks: Seq[ScopedTask[File]]): Initialize[Seq[Artifact]] =
		enabledOnly(artifact, pkgTasks)

	def enabledOnly[T](key: ScopedSetting[T], pkgTasks: Seq[ScopedTask[File]]): Initialize[Seq[T]] =
		( forallIn(key, pkgTasks) zipWith forallIn(publishArtifact, pkgTasks) ) ( _ zip _ collect { case (a, true) => a } )
	def forallIn[T](key: ScopedSetting[T], pkgTasks: Seq[ScopedTask[_]]): Initialize[Seq[T]] =
		pkgTasks.map( pkg => key in pkg.scope in pkg ).join

	val publishSettings: Seq[Setting[_]] = Seq(
		publishMavenStyle in GlobalScope :== true,
		publishArtifact in GlobalScope in Compile :== true,
		publishArtifact in GlobalScope in Test:== false,
		artifacts <<= artifactDefs(defaultArtifactTasks),
		packagedArtifacts <<= packaged(defaultArtifactTasks),
		makePom <<= (ivyModule, makePomConfiguration, streams) map { (module, config, s) => IvyActions.makePom(module, config, s.log); config.file },
		packagedArtifact in makePom <<= (artifact in makePom, makePom) map pairID,
		deliver <<= deliverTask(deliverConfiguration),
		deliverLocal <<= deliverTask(deliverLocalConfiguration),
		publish <<= publishTask(publishConfiguration, deliver),
		publishLocal <<= publishTask(publishLocalConfiguration, deliverLocal)
	)
	val baseSettings: Seq[Setting[_]] = Seq(
		conflictWarning in GlobalScope := ConflictWarning.default,
		unmanagedBase <<= baseDirectory / "lib",
		normalizedName <<= name(StringUtilities.normalize),
		organization <<= organization or normalizedName.identity,
		classpathFilter in GlobalScope :== "*.jar",
		externalResolvers <<= (externalResolvers.task.? zipWith resolvers.identity) {
			case (Some(delegated), Seq()) => delegated
			case (_, rs) => task { Resolver.withDefaultResolvers(rs) }
		},
		fullResolvers <<= (projectResolver,externalResolvers,sbtPlugin,sbtResolver) map { (pr,rs,isPlugin,sr) =>
			val base = pr +: rs
			if(isPlugin) sr +: base else base
		},
		offline in GlobalScope :== false,
		moduleName <<= normalizedName.identity,
		defaultConfiguration in GlobalScope :== Some(Configurations.Compile),
		defaultConfigurationMapping in GlobalScope <<= defaultConfiguration{ case Some(d) => "*->" + d.name; case None => "*->*" },
		ivyPaths <<= (baseDirectory, appConfiguration) { (base, app) => new IvyPaths(base, bootIvyHome(app)) },
		otherResolvers <<= publishTo(_.toList),
		projectResolver <<= projectResolverTask,
		projectDependencies <<= projectDependenciesTask,
		libraryDependencies in GlobalScope :== Nil,
		libraryDependencies <++= (autoScalaLibrary, scalaVersion) { (auto, sv) => if(auto) ScalaArtifacts.libraryDependency(sv) :: Nil else Nil},
		allDependencies <<= (projectDependencies,libraryDependencies,sbtPlugin,sbtDependency) map { (projDeps, libDeps, isPlugin, sbtDep) =>
			val base = projDeps ++ libDeps
			if(isPlugin) sbtDep.copy(configurations = Some(Provided.name)) +: base else base
		},
		ivyLoggingLevel in GlobalScope :== UpdateLogging.Quiet,
		ivyXML in GlobalScope :== NodeSeq.Empty,
		ivyValidate in GlobalScope :== false,
		ivyScala <<= ivyScala or (scalaHome, scalaVersion)((sh,v) => Some(new IvyScala(v, Nil, filterImplicit = true, checkExplicit = true, overrideScalaVersion = sh.isEmpty))),
		moduleConfigurations in GlobalScope :== Nil,
		publishTo in GlobalScope :== None,
		artifactPath in makePom <<= artifactPathSetting(artifact in makePom),
		publishArtifact in makePom <<= publishMavenStyle.identity,
		artifact in makePom <<= moduleName(Artifact.pom),
		projectID <<= (organization,moduleName,version,artifacts,crossPaths){ (org,module,version,as,crossEnabled) =>
			ModuleID(org, module, version).cross(crossEnabled).artifacts(as : _*)
		},
		resolvers in GlobalScope :== Nil,
		projectDescriptors <<= depMap,
		retrievePattern in GlobalScope :== "[type]s/[organisation]/[module]/[artifact](-[revision])(-[classifier]).[ext]",
		updateConfiguration <<= (retrieveConfiguration, ivyLoggingLevel)((conf,level) => new UpdateConfiguration(conf, false, level) ),
		retrieveConfiguration <<= (managedDirectory, retrievePattern, retrieveManaged) { (libm, pattern, enabled) => if(enabled) Some(new RetrieveConfiguration(libm, pattern)) else None },
		ivyConfiguration <<= mkIvyConfiguration,
		ivyConfigurations <<= (autoCompilerPlugins, internalConfigurationMap, thisProject) { (auto, internalMap, project) =>
			(project.configurations ++ project.configurations.map(internalMap) ++ (if(auto) CompilerPlugin :: Nil else Nil)).distinct
		},
		ivyConfigurations ++= Configurations.auxiliary,
		moduleSettings <<= moduleSettings0,
		makePomConfiguration <<= (artifactPath in makePom, pomExtra, pomPostProcess, pomIncludeRepository, pomAllRepositories) {
			(file, extra, process, include, all) => new MakePomConfiguration(file, None, extra, process, include, all)
		},
		deliverLocalConfiguration <<= (crossTarget, ivyLoggingLevel) map { (outDir, level) => deliverConfig( outDir, logging = level ) },
		deliverConfiguration <<= deliverLocalConfiguration.identity,
		publishConfiguration <<= (packagedArtifacts, publishTo, publishMavenStyle, deliver, ivyLoggingLevel) map { (arts, publishTo, mavenStyle, ivyFile, level) =>
			publishConfig(arts, if(mavenStyle) None else Some(ivyFile), resolverName = getPublishTo(publishTo).name, logging = level)
		},
		publishLocalConfiguration <<= (packagedArtifacts, deliverLocal, ivyLoggingLevel) map {
			(arts, ivyFile, level) => publishConfig(arts, Some(ivyFile), logging = level )
		},
		ivySbt <<= (ivyConfiguration, credentials, streams) map { (conf, creds, s) =>
			Credentials.register(creds, s.log)
			new IvySbt(conf)
		},
		ivyModule <<= (ivySbt, moduleSettings) map { (ivySbt, settings) => new ivySbt.Module(settings) },
		update <<= (ivyModule, thisProjectRef, updateConfiguration, cacheDirectory, scalaInstance, streams) map { (module, ref, config, cacheDirectory, si, s) =>
			cachedUpdate(cacheDirectory / "update", Project.display(ref), module, config, Some(si), s.log)
		},
		update <<= (conflictWarning, update, streams) map { (config, report, s) => ConflictWarning(config, report, s.log); report },
		transitiveClassifiers in GlobalScope :== Seq(SourceClassifier, DocClassifier),
		updateClassifiers <<= (ivySbt, projectID, update, transitiveClassifiers in updateClassifiers, updateConfiguration, ivyScala, target in LocalRootProject, appConfiguration, streams) map { (is, pid, up, classifiers, c, ivyScala, out, app, s) =>
			withExcludes(out, classifiers, lock(app)) { excludes =>
				IvyActions.updateClassifiers(is, GetClassifiersConfiguration(pid, up.allModules, classifiers, excludes, c, ivyScala), s.log)
			}
		},
		updateSbtClassifiers <<= (ivySbt, projectID, transitiveClassifiers in updateSbtClassifiers, updateConfiguration, sbtDependency, ivyScala, target in LocalRootProject, appConfiguration, streams) map {
				(is, pid, classifiers, c, sbtDep, ivyScala, out, app, s) =>
			withExcludes(out, classifiers, lock(app)) { excludes =>
				IvyActions.transitiveScratch(is, "sbt", GetClassifiersConfiguration(pid, sbtDep :: Nil, classifiers, excludes, c, ivyScala), s.log)
			}
		},
		sbtResolver in GlobalScope :== typesafeResolver,
		sbtDependency in GlobalScope <<= appConfiguration { app =>
			val id = app.provider.id
			val base = ModuleID(id.groupID, id.name, id.version, crossVersion = id.crossVersioned)
			IvySbt.substituteCross(base, app.provider.scalaProvider.version).copy(crossVersion = false)
		}
	)

	def moduleSettings0: Initialize[Task[ModuleSettings]] =
		(projectID, allDependencies, ivyXML, ivyConfigurations, defaultConfiguration, ivyScala, ivyValidate) map {
			(pid, deps, ivyXML, confs, defaultConf, ivyS, validate) => new InlineConfiguration(pid, deps, ivyXML, confs, defaultConf, ivyS, validate)
		}
	def deliverTask(config: TaskKey[DeliverConfiguration]): Initialize[Task[File]] =
		(ivyModule, config, update, streams) map { (module, config, _, s) => IvyActions.deliver(module, config, s.log) }
	def publishTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]): Initialize[Task[Unit]] =
		(ivyModule, config, streams) map { (module, config, s) =>
			IvyActions.publish(module, config, s.log)
		}

		import Cache._
		import CacheIvy.{classpathFormat, /*publishIC,*/ updateIC, updateReportF, excludeMap}

	def withExcludes(out: File, classifiers: Seq[String], lock: xsbti.GlobalLock)(f: Map[ModuleID, Set[String]] => UpdateReport): UpdateReport =
	{
		val exclName = "exclude_classifiers"
		val file = out / exclName
		lock(out / (exclName + ".lock"), new Callable[UpdateReport] { def call = {
			val excludes = CacheIO.fromFile[Map[ModuleID, Set[String]]](excludeMap, Map.empty[ModuleID, Set[String]])(file)
			val report = f(excludes)
			val allExcludes = excludes ++ IvyActions.extractExcludes(report)
			CacheIO.toFile(excludeMap)(allExcludes)(file)
			IvyActions.addExcluded(report, classifiers, allExcludes)
		}})
	}

	def cachedUpdate(cacheFile: File, label: String, module: IvySbt#Module, config: UpdateConfiguration, scalaInstance: Option[ScalaInstance], log: Logger): UpdateReport =
	{
		implicit val updateCache = updateIC
		implicit val updateReport = updateReportF
		type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil
		def work = (_:  In) match { case conf :+: settings :+: config :+: HNil =>
			log.info("Updating " + label + "...")
			val r = IvyActions.update(module, config, log)
			log.info("Done updating.")
			scalaInstance match { case Some(si) => substituteScalaFiles(si, r); case None => r }
		}

		val f =
			Tracked.inputChanged(cacheFile / "inputs") { (inChanged: Boolean, in: In) =>
				val outCache = Tracked.lastOutput[In, UpdateReport](cacheFile / "output") {
					case (_, Some(out)) if !inChanged && out.allFiles.forall(_.exists) && out.cachedDescriptor.exists => out
					case _ => work(in)
				}
				outCache(in)
			}
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

	def defaultRepositoryFilter = (repo: MavenRepository) => !repo.root.startsWith("file:")
	def getPublishTo(repo: Option[Resolver]): Resolver = repo getOrElse error("Repository for publishing is not specified.")

	def deliverConfig(outputDirectory: File, status: String = "release", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
	    new DeliverConfiguration(deliverPattern(outputDirectory), status, None, logging)
	def publishConfig(artifacts: Map[Artifact, File], ivyFile: Option[File], resolverName: String = "local", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
	    new PublishConfiguration(ivyFile, resolverName, artifacts, logging)

	def deliverPattern(outputPath: File): String  =  (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

	def projectDependenciesTask =
		(thisProject, settings) map { (p, data) =>
			p.dependencies flatMap { dep => (projectID in dep.project) get data map { _.copy(configurations = dep.configuration) } }
		}

	def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
		(thisProject, thisProjectRef, settings, streams) flatMap { (root, rootRef, data, s) =>
			val dependencies = (p: (ProjectRef, ResolvedProject)) => p._2.dependencies.flatMap(pr => thisProject in pr.project get data map { (pr.project, _) })
			depMap(Dag.topologicalSort((rootRef,root))(dependencies).dropRight(1), data, s.log)
		}

	def depMap(projects: Seq[(ProjectRef,ResolvedProject)], data: Settings[Scope], log: Logger): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		projects.flatMap { case (p,_) => ivyModule in p get data }.join.map { mods =>
			mods map { _.dependencyMapping(log) } toMap ;
		}

	def projectResolverTask: Initialize[Task[Resolver]] =
		projectDescriptors map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	def analyzed[T](data: T, analysis: inc.Analysis) = Attributed.blank(data).put(Keys.analysis, analysis)
	def makeProducts: Initialize[Task[Seq[File]]] = 
		(compile, compileInputs, copyResources) map { (_, i, _) => i.config.classesDirectory :: Nil }
	def exportProductsTask: Initialize[Task[Classpath]] =
		(products.task, packageBin.task, exportJars, compile) flatMap { (psTask, pkgTask, useJars, analysis) =>
			(if(useJars) Seq(pkgTask).join else psTask) map { _ map { f => analyzed(f, analysis) } }
		}

	def internalDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, thisProject, classpathConfiguration, configuration, settings) flatMap internalDependencies0
	def unmanagedDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, thisProject, configuration, settings) flatMap unmanagedDependencies0
	def mkIvyConfiguration: Initialize[Task[IvyConfiguration]] =
		(fullResolvers, ivyPaths, otherResolvers, moduleConfigurations, offline, checksums, appConfiguration, streams) map { (rs, paths, other, moduleConfs, off, check, app, s) =>
			new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, Some(lock(app)), check, s.log)
		}

		import java.util.LinkedHashSet
		import collection.JavaConversions.asScalaSet
	def interSort(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, data: Settings[Scope]): Seq[(ProjectRef,String)] =
	{
		val visited = asScalaSet(new LinkedHashSet[(ProjectRef,String)])
		def visit(p: ProjectRef, project: ResolvedProject, c: Configuration)
		{
			val applicableConfigs = allConfigs(c)
			for(ac <- applicableConfigs) // add all configurations in this project
				visited add (p, ac.name)
			val masterConfs = names(getConfigurations(projectRef, data))

			for( ResolvedClasspathDependency(dep, confMapping) <- project.dependencies)
			{
				val depProject = thisProject in dep get data getOrElse error("Invalid project: " + dep)
				val configurations = getConfigurations(dep, data)
				val mapping = mapped(confMapping, masterConfs, names(configurations), "compile", "*->compile")
				// map master configuration 'c' and all extended configurations to the appropriate dependency configuration
				for(ac <- applicableConfigs; depConfName <- mapping(ac.name))
				{
					for(depConf <- confOpt(configurations, depConfName) )
						if( ! visited( (dep, depConfName) ) )
							visit(dep, depProject, depConf)
				}
			}
		}
		visit(projectRef, project, conf)
		visited.toSeq
	}
	def unmanagedDependencies0(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, conf, data, true, unmanagedLibs)
	def internalDependencies0(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, self: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, self, data, false, productsTask)
	def interDependencies(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, self: Configuration, data: Settings[Scope], includeSelf: Boolean,
		f: (ProjectRef, String, Settings[Scope]) => Task[Classpath]): Task[Classpath] =
	{
		val visited = interSort(projectRef, project, conf, data)
		val tasks = asScalaSet(new LinkedHashSet[Task[Classpath]])
		for( (dep, c) <- visited )
			if(includeSelf || (dep != projectRef) || (conf.name != c && self.name != c))
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

	def parseList(s: String, allConfs: Seq[String]): Seq[String] = (trim(s split ",") flatMap replaceWildcard(allConfs)).distinct
	def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] =
		if(conf == "") Nil else if(conf == "*") allConfs else conf :: Nil
	
	private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)
	def missingConfiguration(in: String, conf: String) =
		error("Configuration '" + conf + "' not defined in '" + in + "'")
	def allConfigs(conf: Configuration): Seq[Configuration] =
		Dag.topologicalSort(conf)(_.extendsConfigs)

	def getConfigurations(p: ResolvedReference, data: Settings[Scope]): Seq[Configuration] =
		ivyConfigurations in p get data getOrElse Nil
	def confOpt(configurations: Seq[Configuration], conf: String): Option[Configuration] =
		configurations.find(_.name == conf)
	def productsTask(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		getClasspath(exportedProducts, dep, conf, data)
	def unmanagedLibs(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		getClasspath(unmanagedJars, dep, conf, data)
	def getClasspath(key: TaskKey[Classpath], dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		( key in (dep, ConfigKey(conf)) ) get data getOrElse const(Nil)
	def defaultConfigurationTask(p: ResolvedReference, data: Settings[Scope]): Configuration =
		flatten(defaultConfiguration in p get data) getOrElse Configurations.Default
	def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap idFun

	lazy val typesafeResolver = Resolver.url("typesafe-ivy-releases", new URL("http://repo.typesafe.com/typesafe/ivy-releases/"))(Resolver.ivyStylePatterns)

		import DependencyFilter._
	def managedJars(config: Configuration, jarTypes: Set[String], up: UpdateReport): Classpath =
		up.filter( configurationFilter(config.name) && artifactFilter(`type` = jarTypes) ).toSeq.map { case (conf, module, art, file) =>
			Attributed(file)(AttributeMap.empty.put(artifact.key, art).put(moduleID.key, module).put(configuration.key, config))
		} distinct;

	def autoPlugins(report: UpdateReport): Seq[String] =
	{
		val pluginClasspath = report matching configurationFilter(CompilerPlugin.name)
		classpath.ClasspathUtilities.compilerPlugins(pluginClasspath).map("-Xplugin:" + _.getAbsolutePath).toSeq
	}

	lazy val compilerPluginConfig = Seq(
		scalacOptions <<= (scalacOptions, autoCompilerPlugins, update) map { (options, auto, report) =>
			if(auto) options ++ autoPlugins(report) else options
		}
	)
	def substituteScalaFiles(scalaInstance: ScalaInstance, report: UpdateReport): UpdateReport =
		report.substitute { (configuration, module, arts) =>
			import ScalaArtifacts._
			(module.organization, module.name) match
			{
				case (Organization, LibraryID) => (Artifact(LibraryID), scalaInstance.libraryJar) :: Nil
				case (Organization, CompilerID) => (Artifact(CompilerID), scalaInstance.compilerJar) :: Nil
				case _ => arts
			}
		}

		// try/catch for supporting earlier launchers
	def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
		try { Option(app.provider.scalaProvider.launcher.ivyHome) }
		catch { case _: NoSuchMethodError => None }
}

trait BuildExtra extends BuildCommon
{
		import Defaults._

	def compilerPlugin(dependency: ModuleID): ModuleID =
		dependency.copy(configurations = Some("plugin->default(compile)"))

	def addCompilerPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
		libraryDependencies += compilerPlugin(dependency)

	def addArtifact(a: Artifact, taskDef: ScopedTask[File]): SettingsDefinition =
	{
		val pkgd = packagedArtifacts <<= (packagedArtifacts, taskDef) map ( (pas,file) => pas updated (a, file) )
		seq( artifacts += a, pkgd )
	}
	def addArtifact(artifact: ScopedSetting[Artifact], taskDef: ScopedTask[File]): SettingsDefinition =
	{
		val art = artifacts <<= (artifact, artifacts)( _ +: _ )
		val pkgd = packagedArtifacts <<= (packagedArtifacts, artifact, taskDef) map ( (pas,a,file) => pas updated (a, file))
		seq( art, pkgd )
	}

	def seq(settings: Setting[_]*): SettingsDefinition = new Project.SettingList(settings)
	implicit def settingsDefinitionToSeq(sd: SettingsDefinition): Seq[Setting[_]] = sd.settings

	def externalIvySettings(file: Initialize[File] = baseDirectory / "ivysettings.xml"): Setting[Task[IvyConfiguration]] =
	{
		val other = (baseDirectory, appConfiguration, streams).identityMap
		ivyConfiguration <<= (file zipWith other) { case (f, otherTask) =>
			otherTask map { case (base, app, s) => new ExternalIvyConfiguration(base, f, Some(lock(app)), s.log) }
		}
	}
	def externalIvyFile(file: Initialize[File] = baseDirectory / "ivy.xml", iScala: Initialize[Option[IvyScala]] = ivyScala.identity): Setting[Task[ModuleSettings]] =
		external(file, iScala)( (f, is, v) => new IvyFileConfiguration(f, is, v) ) 
	def externalPom(file: Initialize[File] = baseDirectory / "pom.xml", iScala: Initialize[Option[IvyScala]] = ivyScala.identity): Setting[Task[ModuleSettings]] =
		external(file, iScala)( (f, is, v) => new PomConfiguration(f, is, v) )

	private[this] def external(file: Initialize[File], iScala: Initialize[Option[IvyScala]])(make: (File, Option[IvyScala], Boolean) => ModuleSettings): Setting[Task[ModuleSettings]] =
		moduleSettings <<= ((file zip iScala) zipWith ivyValidate.identity) { case ((f, is), v) => task { make(f, is, v) } }

	def runInputTask(config: Configuration, mainClass: String, baseArguments: String*): Initialize[InputTask[Unit]] =
		inputTask { result =>
			(fullClasspath in config, runner in (config, run), streams, result) map { (cp, r, s, args) =>
				toError(r.run(mainClass, data(cp), baseArguments ++ args, s.log))
			}
		}
	def runTask(config: Configuration, mainClass: String, arguments: String*): Initialize[Task[Unit]] =
		(fullClasspath in config, runner in (config, run), streams) map { (cp, r, s) =>
			toError(r.run(mainClass, data(cp), arguments, s.log))
		}
	
	def fullRunInputTask(scoped: ScopedInput[Unit], config: Configuration, mainClass: String, baseArguments: String*): Setting[InputTask[Unit]] =
		scoped <<= inputTask { result =>
			( inScoped(scoped.scoped, runnerInit) zipWith (fullClasspath in config, streams, result).identityMap) { (r, t) =>
				t map { case (cp, s, args) =>
					toError(r.run(mainClass, data(cp), baseArguments ++ args, s.log))
				}
			}
		}
	def fullRunTask(scoped: ScopedTask[Unit], config: Configuration, mainClass: String, arguments: String*): Setting[Task[Unit]] =
		scoped <<= ( inScoped(scoped.scoped, runnerInit) zipWith (fullClasspath in config, streams).identityMap ) { case (r, t) =>
			t map { case (cp, s) =>
				toError(r.run(mainClass, data(cp), arguments, s.log))
			}
		}
	private[this] def inScoped[T](sk: ScopedKey[_], i: Initialize[T]): Initialize[T]  =  inScope(fillTaskAxis(sk.scope, sk.key), i)
	private[this] def inScope[T](s: Scope, i: Initialize[T]): Initialize[T]  =  i mapReferenced Project.mapScope(Scope.replaceThis(s))
	
	/** Disables post-compilation hook for determining tests for tab-completion (such as for 'test-only').
	* This is useful for reducing test:compile time when not running test. */
	def noTestCompletion(config: Configuration = Test): Setting[_]  =  inConfig(config)( Seq(definedTests <<= Defaults.detectTests) ).head
}
trait BuildCommon
{
	def inputTask[T](f: TaskKey[Seq[String]] => Initialize[Task[T]]): Initialize[InputTask[T]] = InputTask(_ => complete.Parsers.spaceDelimited("<arg>"))(f)

	implicit def globFilter(expression: String): NameFilter = GlobFilter(expression)
	implicit def richAttributed(s: Seq[Attributed[File]]): RichAttributed = new RichAttributed(s)
	implicit def richFiles(s: Seq[File]): RichFiles = new RichFiles(s)
	implicit def richPathFinder(s: PathFinder): RichPathFinder = new RichPathFinder(s)
	final class RichPathFinder private[sbt](s: PathFinder)
	{
		def classpath: Classpath = Attributed blankSeq s.get
	}
	final class RichAttributed private[sbt](s: Seq[Attributed[File]])
	{
		def files: Seq[File] = Build data s
	}
	final class RichFiles private[sbt](s: Seq[File])
	{
		def classpath: Classpath = Attributed blankSeq s
	}
	def toError(o: Option[String]): Unit = o foreach error
}