/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Build.data
	import Scope.{fillTaskAxis, GlobalScope, ThisScope}
	import xsbt.api.Discovery
	import Project.{inConfig, Initialize, inScope, inTask, ScopedKey, Setting, SettingsDefinition}
	import Load.LoadedBuild
	import Artifact.{DocClassifier, SourceClassifier}
	import Configurations.{Compile, CompilerPlugin, IntegrationTest, names, Provided, Runtime, Test}
	import complete._
	import std.TaskExtra._
	import inc.{FileValueCache, Locate}
	import org.scalatools.testing.{AnnotatedFingerprint, SubclassFingerprint}

	import sys.error
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
	def configSrcSub(key: SettingKey[File]): Initialize[File] = (key in ThisScope.copy(config = Global), configuration) { (src, conf) => src / nameForSrc(conf.name) }
	def nameForSrc(config: String) = if(config == "compile") "main" else config
	def prefix(config: String) = if(config == "compile") "" else config + "-"

	def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = app.provider.scalaProvider.launcher.globalLock

	def extractAnalysis[T](a: Attributed[T]): (T, inc.Analysis) = 
		(a.data, a.metadata get Keys.analysis getOrElse inc.Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, inc.Analysis] =
		(for(a <- cp; an <- a.metadata get Keys.analysis) yield (a.data, an) ).toMap

	def buildCore: Seq[Setting[_]] = thisBuildCore ++ globalCore
	def thisBuildCore: Seq[Setting[_]] = inScope(GlobalScope.copy(project = Select(ThisBuild)))(Seq(
		managedDirectory <<= baseDirectory(_ / "lib_managed")
	))
	def globalCore: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
		buildDependencies <<= buildDependencies or Classpaths.constructBuildDependencies,
		taskTemporaryDirectory := IO.createTemporaryDirectory,
		onComplete <<= taskTemporaryDirectory { dir => () => IO.delete(dir); IO.createDirectory(dir) },
		parallelExecution :== true,
		sbtVersion in GlobalScope <<= appConfiguration { _.provider.id.version },
		sbtResolver in GlobalScope <<= sbtVersion { sbtV => if(sbtV endsWith "-SNAPSHOT") Classpaths.typesafeSnapshots else Classpaths.typesafeResolver },
		pollInterval :== 500,
		logBuffered :== false,
		connectInput :== false,
		cancelable :== false,
		autoScalaLibrary :== true,
		onLoad <<= onLoad ?? idFun[State],
		onUnload <<= (onUnload ?? idFun[State]),
		onUnload <<= (onUnload, taskTemporaryDirectory) { (f, dir) => s => { try f(s) finally IO.delete(dir) } },
		watchingMessage <<= watchingMessage ?? Watched.defaultWatchingMessage,
		triggeredMessage <<= triggeredMessage ?? Watched.defaultTriggeredMessage,
		definesClass :== FileValueCache(Locate.definesClass _ ).get,
		trapExit :== false,
		trapExit in run :== true,
		traceLevel in run :== 0,
		traceLevel in runMain :== 0,
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
		skip :== false,
		watchSources :== Nil,
		version :== "0.1-SNAPSHOT",
		outputStrategy :== None,
		exportJars :== false,
		fork :== false,
		javaOptions :== Nil,
		sbtPlugin :== false,
		crossPaths :== true,
		classpathTypes :== Set("jar", "bundle"),
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
		checksums <<= appConfiguration(Classpaths.bootChecksums),
		pomExtra :== NodeSeq.Empty,
		pomPostProcess :== idFun,
		pomAllRepositories :== false,
		includeFilter :== NothingFilter,
		includeFilter in unmanagedSources :== "*.java" | "*.scala",
		includeFilter in unmanagedJars :== "*.jar" | "*.so" | "*.dll",
		includeFilter in unmanagedResources :== AllPassFilter,
		excludeFilter :== (".*"  - ".") || HiddenFileFilter,
		pomIncludeRepository :== Classpaths.defaultRepositoryFilter
	))
	def projectCore: Seq[Setting[_]] = Seq(
		name <<= thisProject(_.id),
		logManager <<= extraLoggers(LogManager.defaults),
		onLoadMessage <<= onLoadMessage or (name, thisProjectRef)("Set current project to " + _ + " (in build " + _.build +")"),
		runnerTask
	)
	def paths = Seq(
		baseDirectory <<= thisProject(_.base),
		target <<= baseDirectory / "target",
		historyPath <<= target(t => Some(t / ".history")),
		sourceDirectory <<= baseDirectory / "src",
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
			// remove when sourceFilter, defaultExcludes are removed
		includeFilter in unmanagedSources <<= (sourceFilter in unmanagedSources) or (includeFilter in unmanagedSources),
		excludeFilter in unmanagedSources <<= (defaultExcludes in unmanagedSources) or (excludeFilter in unmanagedSources),
		unmanagedSources <<= collectFiles(unmanagedSourceDirectories, includeFilter in unmanagedSources, excludeFilter in unmanagedSources),
		watchSources in ConfigGlobal <++= unmanagedSources,
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
			// remove when defaultExcludes are removed
		excludeFilter in unmanagedResources <<= (defaultExcludes in unmanagedResources) or (excludeFilter in unmanagedResources),
		unmanagedResources <<= collectFiles(unmanagedResourceDirectories, includeFilter in unmanagedResources, excludeFilter in unmanagedResources),
		watchSources in ConfigGlobal <++= unmanagedResources,
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
		unmanagedSources <<= (unmanagedSources, baseDirectory, includeFilter in unmanagedSources, excludeFilter in unmanagedSources) map {
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
		crossTarget <<= (target, scalaVersion, sbtVersion, sbtPlugin, crossPaths)(makeCrossTarget),
		cacheDirectory <<= crossTarget / "cache"
	)
	def makeCrossTarget(t: File, sv: String, sbtv: String, plugin: Boolean, cross: Boolean): File =
	{
		val scalaBase = if(cross) t / ("scala-" + sv) else t
		if(plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
	}
	def compilersSetting = compilers <<= (scalaInstance, appConfiguration, streams, classpathOptions, javaHome) map { (si, app, s, co, jh) => Compiler.compilers(si, co, jh)(app, s.log) }

	lazy val configTasks = docSetting(doc) ++ Seq(
		initialCommands in GlobalScope :== "",
		cleanupCommands in GlobalScope :== "",
		compile <<= compileTask,
		compileInputs <<= compileInputsTask,
		compileIncSetup <<= compileIncSetupTask,
		console <<= consoleTask,
		consoleQuick <<= consoleQuickTask,
		discoveredMainClasses <<= compile map discoverMainClasses storeAs discoveredMainClasses triggeredBy compile,
		definedSbtPlugins <<= discoverPlugins,
		inTask(run)(runnerTask :: Nil).head,
		selectMainClass <<= discoveredMainClasses map selectRunMain,
		mainClass in run <<= selectMainClass in run,
		mainClass <<= discoveredMainClasses map selectPackageMain,
		run <<= runTask(fullClasspath, mainClass in run, runner in run),
		runMain <<= runMainTask(fullClasspath, runner in run),
		copyResources <<= copyResourcesTask
	)

	lazy val projectTasks: Seq[Setting[_]] = Seq(
		cleanFiles <<= Seq(managedDirectory, target).join,
		cleanKeepFiles <<= historyPath(_.toList),
		clean <<= (cleanFiles, cleanKeepFiles) map doClean,
		consoleProject <<= consoleProjectTask,
		watchTransitiveSources <<= watchTransitiveSourcesTask,
		watch <<= watchSetting
	)

	def generate(generators: SettingKey[Seq[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] = generators {_.join.map(_.flatten) }

	def inAllConfigurations[T](key: TaskKey[T]): Initialize[Task[Seq[T]]] = (state, thisProjectRef) flatMap { (state, ref) =>
		val structure = Project structure state
		val configurations = Project.getProject(ref, structure).toList.flatMap(_.configurations)
		configurations.flatMap { conf =>
			key in (ref, conf) get structure.data
		} join
	}
	def watchTransitiveSourcesTask: Initialize[Task[Seq[File]]] =
		inDependencies[Task[Seq[File]]](watchSources.task, const(std.TaskExtra.constant(Nil)), aggregate = true, includeRoot = true) apply { _.join.map(_.flatten) }

	def transitiveUpdateTask: Initialize[Task[Seq[UpdateReport]]] =
		forDependencies(ref => (update.task in ref).?, aggregate = false, includeRoot = false) apply( _.flatten.join)

	def watchSetting: Initialize[Watched] = (pollInterval, thisProjectRef, watchingMessage, triggeredMessage) { (interval, base, msg, trigMsg) =>
		new Watched {
			val scoped = watchTransitiveSources in base
			val key = ScopedKey(scoped.scope, scoped.key)
			override def pollInterval = interval
			override def watchingMessage(s: WatchState) = msg(s)
			override def triggeredMessage(s: WatchState) = trigMsg(s)
			override def watchPaths(s: State) = EvaluateTask.evaluateTask(Project structure s, key, s, base) match {
				case Some(Value(ps)) => ps
				case Some(Inc(i)) => throw i
				case None => error("key not found: " + Project.displayFull(key))
			}
		}
	}
	def scalaInstanceSetting = (appConfiguration, scalaVersion, scalaHome) map { (app, version, home) =>
		val launcher = app.provider.scalaProvider.launcher
		home match {
			case None => ScalaInstance(version, launcher)
			case Some(h) => ScalaInstance(h, launcher)
		}
	}

	lazy val testTasks: Seq[Setting[_]] = testTaskOptions(test) ++ testTaskOptions(testOnly) ++ Seq(	
		testLoader <<= (fullClasspath, scalaInstance, taskTemporaryDirectory) map { (cp, si, temp) => TestFramework.createTestLoader(data(cp), si, IO.createUniqueDirectory(temp)) },
		testFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs2, Specs, ScalaTest, ScalaCheckCompat, ScalaTestCompat, SpecsCompat, JUnit)
		},
		loadedTestFrameworks <<= (testFrameworks, streams, testLoader) map { (frameworks, s, loader) =>
			frameworks.flatMap(f => f.create(loader, s.log).map( x => (f,x)).toIterable).toMap
		},
		definedTests <<= detectTests,
		definedTestNames <<= definedTests map ( _.map(_.name).distinct) storeAs definedTestNames triggeredBy compile,
		testListeners in GlobalScope :== Nil,
		testOptions in GlobalScope :== Nil,
		executeTests <<= (streams in test, loadedTestFrameworks, parallelExecution in test, testOptions in test, testLoader, definedTests, resolvedScoped, state) flatMap {
			(s, frameworkMap, par, options, loader, discovered, scoped, st) =>
				implicit val display = Project.showContextKey(st)
				Tests(frameworkMap, loader, discovered, options, par, noTestsMessage(ScopedKey(scoped.scope, test.key)), s.log)
		},
		test <<= (executeTests, streams) map { (results, s) => Tests.showResults(s.log, results) },
		testOnly <<= testOnlyTask
	)
	private[this] def noTestsMessage(scoped: ScopedKey[_])(implicit display: Show[ScopedKey[_]]): String =
		"No tests to run for " + display(scoped)

	lazy val TaskGlobal: Scope = ThisScope.copy(task = Global)
	lazy val ConfigGlobal: Scope = ThisScope.copy(config = Global)
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
	InputTask( loadForParser(definedTestNames)( (s, i) => testOnlyParser(s, i getOrElse Nil) ) ) { result =>  
		(streams, loadedTestFrameworks, parallelExecution in testOnly, testOptions in testOnly, testLoader, definedTests, resolvedScoped, result, state) flatMap {
			case (s, frameworks, par, opts, loader, discovered, scoped, (tests, frameworkOptions), st) =>
				val filter = selectedFilter(tests)
				val modifiedOpts = Tests.Filter(filter) +: Tests.Argument(frameworkOptions : _*) +: opts
				implicit val display = Project.showContextKey(st)
				Tests(frameworks, loader, discovered, modifiedOpts, par, noTestsMessage(scoped), s.log) map { results =>
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
		artifact <<= moduleName(n => Artifact(n)),
		packageOptions in GlobalScope :== Nil,
		artifactName in GlobalScope :== ( Artifact.artifactName _ )
	)
	lazy val packageConfig: Seq[Setting[_]] = Seq(
		packageOptions in packageBin <<= (packageOptions, mainClass in packageBin, name, version, homepage, organization, organizationName) map { (p, main, name, ver, h, org, orgName) =>
			Package.addSpecManifestAttributes(name, ver, orgName) +: Package.addImplManifestAttributes(name, ver, h, org, orgName) +: main.map(Package.MainClass.apply) ++: p },
		packageOptions in packageSrc <<= (name, version, organizationName, packageOptions) map { Package.addSpecManifestAttributes(_, _, _) +: _ },
		`package` <<= packageBin
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

	def artifactPathSetting(art: SettingKey[Artifact])  =  (crossTarget, projectID, art, scalaVersion in artifactName, artifactName) { (t, module, a, sv, toString) => t / toString(sv, module, a) asFile }
	def artifactSetting = ((artifact, artifactClassifier).identity zipWith configuration.?) { case ((a,classifier),cOpt) =>
		val cPart = cOpt flatMap { c => if(c == Compile) None else Some(c.name) }
		val combined = cPart.toList ++ classifier.toList
		if(combined.isEmpty) a.copy(classifier = None, configurations = cOpt.toList) else {
			val classifierString = combined mkString "-"
			val confs = cOpt.toList flatMap { c => artifactConfigurations(a, c, classifier) } 
			a.copy(classifier = Some(classifierString), `type` = Artifact.classifierType(classifierString), configurations = confs)
		}
	}
	def artifactConfigurations(base: Artifact, scope: Configuration, classifier: Option[String]): Iterable[Configuration] =
		if(base.configurations.isEmpty)
			classifier match {
				case Some(c) => Artifact.classifierConf(c) :: Nil
				case None => scope :: Nil
			}
		else
			base.configurations
	def pairID[A,B] = (a: A, b: B) => (a,b)
	def packageTasks(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in TaskGlobal <<= packageTask,
			packageConfiguration <<= packageConfigurationTask,
			mappings <<= mappingsTask,
			packagedArtifact <<= (artifact, key) map pairID,
			artifact <<= artifactSetting,
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
	def runMainTask(classpath: TaskKey[Classpath], scalaRun: TaskKey[ScalaRun]): Initialize[InputTask[Unit]] =
	{
			import DefaultParsers._
		InputTask( loadForParser(discoveredMainClasses)( (s, names) => runMainParser(s, names getOrElse Nil) ) ) { result =>
			(classpath, scalaRun, streams, result) map { case (cp, runner, s, (mainClass, args)) =>
				toError(runner.run(mainClass, data(cp), args, s.log))
			}
		}
	}

	def runTask(classpath: TaskKey[Classpath], mainClassTask: TaskKey[Option[String]], scalaRun: TaskKey[ScalaRun]): Initialize[InputTask[Unit]] =
		inputTask { result =>
			(classpath, mainClassTask, scalaRun, streams, result) map { (cp, main, runner, s, args) =>
				val mainClass = main getOrElse error("No main class detected.")
				toError(runner.run(mainClass, data(cp), args, s.log))
			}
		}

	def runnerTask = runner <<= runnerInit
	def runnerInit: Initialize[Task[ScalaRun]] = 
		(taskTemporaryDirectory, scalaInstance, baseDirectory, javaOptions, outputStrategy, fork, javaHome, trapExit, connectInput) map {
				(tmp, si, base, options, strategy, forkRun, javaHomeDir, trap, connectIn) =>
			if(forkRun) {
				new ForkRun( ForkOptions(scalaJars = si.jars, javaHome = javaHomeDir, connectInput = connectIn, outputStrategy = strategy,
					runJVMOptions = options, workingDirectory = Some(base)) )
			} else
				new Run(si, trap, tmp)
		}

	def docSetting(key: TaskKey[File]): Seq[Setting[_]] = inTask(key)(Seq(
		cacheDirectory ~= (_ / key.key.label),
		target <<= docDirectory, // deprecate docDirectory in favor of 'target in doc'; remove when docDirectory is removed
		scaladocOptions <<= scalacOptions, // deprecate scaladocOptions in favor of 'scalacOptions in doc'; remove when scaladocOptions is removed
		fullClasspath <<= dependencyClasspath,
		key in TaskGlobal <<= (sources, cacheDirectory, maxErrors, compilers, target, configuration, scaladocOptions, fullClasspath, streams) map { (srcs, cache, maxE, cs, out, config, options, cp, s) =>
			(new Scaladoc(maxE, cs.scalac)).cached(cache, nameForSrc(config.name), srcs, cp.files, out, options, s.log)
			out
		}
	))
		
	@deprecated("Use `docSetting` instead", "0.11.0") def docTask: Initialize[Task[File]] =
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
	def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] = (compilers in task, classpath in task, scalacOptions in task, initialCommands in task, cleanupCommands in task, streams) map {
		(cs, cp, options, initCommands, cleanup, s) =>
			(new Console(cs.scalac))(data(cp), options, initCommands, cleanup, s.log).foreach(msg => error(msg))
			println()
	}
	
	def compileTask = (compileInputs, streams) map { (i,s) => Compiler(i,s.log) }
	def compileIncSetupTask = 
		(dependencyClasspath, cacheDirectory, skip in compile, definesClass) map { (cp, cacheDir, skip, definesC) =>
			Compiler.IncSetup(analysisMap(cp), definesC, skip, cacheDir / "compile")
		}
	def compileInputsTask =
		(dependencyClasspath, sources, compilers, javacOptions, scalacOptions, classDirectory, compileOrder, compileIncSetup, streams) map {
		(cp, srcs, cs, javacOpts, scalacOpts, classes, order, incSetup, s) =>
			val classpath = classes +: data(cp)
			Compiler.inputs(classpath, srcs, classes, scalacOpts, javacOpts, 100, order)(cs, incSetup, s.log)
		}
		
	def sbtPluginExtra(m: ModuleID, sbtV: String, scalaV: String): ModuleID  =  m.extra(CustomPomParser.SbtVersionKey -> sbtV, CustomPomParser.ScalaVersionKey -> scalaV).copy(crossVersion = false)
	def writePluginsDescriptor(plugins: Set[String], dir: File): Seq[File] =
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

	def inDependencies[T](key: SettingKey[T], default: ProjectRef => T, includeRoot: Boolean = true, classpath: Boolean = true, aggregate: Boolean = false): Initialize[Seq[T]] =
		forDependencies[T,T](ref => (key in ref) ?? default(ref), includeRoot, classpath, aggregate)

	def forDependencies[T,V](init: ProjectRef => Initialize[V], includeRoot: Boolean = true, classpath: Boolean = true, aggregate: Boolean = false): Initialize[Seq[V]] =
		Project.bind( (loadedBuild, thisProjectRef).identity ) { case (lb, base) =>
			transitiveDependencies(base, lb, includeRoot, classpath, aggregate) map init join ;
		}

	def transitiveDependencies(base: ProjectRef, structure: LoadedBuild, includeRoot: Boolean, classpath: Boolean = true, aggregate: Boolean = false): Seq[ProjectRef] =
	{
		val full = Dag.topologicalSort(base)(getDependencies(structure, classpath, aggregate))
		if(includeRoot) full else full.dropRight(1)
	}
	def getDependencies(structure: LoadedBuild, classpath: Boolean = true, aggregate: Boolean = false): ProjectRef => Seq[ProjectRef] =
		ref => Project.getProject(ref, structure).toList flatMap { p =>
			(if(classpath) p.dependencies.map(_.project) else Nil) ++
			(if(aggregate) p.aggregate else Nil)
		}

	val CompletionsID = "completions"
	
	def noAggregation: Seq[Scoped] = Seq(run, console, consoleQuick, consoleProject)
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
	def concatSettings[T](a: SettingKey[Seq[T]], b: SettingKey[Seq[T]]): Initialize[Seq[T]] = (a,b)(_ ++ _)

	lazy val configSettings: Seq[Setting[_]] = Seq(
		externalDependencyClasspath <<= concat(unmanagedClasspath, managedClasspath),
		dependencyClasspath <<= concat(internalDependencyClasspath, externalDependencyClasspath),
		fullClasspath <<= concatDistinct(exportedProducts, dependencyClasspath),
		internalDependencyClasspath <<= internalDependencies,
		unmanagedClasspath <<= unmanagedDependencies,
		products <<= makeProducts,
		productDirectories <<= compileInputs map (_.config.classesDirectory :: Nil),
		exportedProducts <<= exportProductsTask,
		classpathConfiguration <<= (internalConfigurationMap, configuration, classpathConfiguration.?, update.task) apply findClasspathConfig,
		managedClasspath <<= (classpathConfiguration, classpathTypes, update) map managedJars,
			// remove when defaultExcludes and classpathFilter are removed
		excludeFilter in unmanagedJars <<= (defaultExcludes in unmanagedJars) or (excludeFilter in unmanagedJars),
		includeFilter in unmanagedJars <<= classpathFilter or (includeFilter in unmanagedJars),
		unmanagedJars <<= (configuration, unmanagedBase, includeFilter in unmanagedJars, excludeFilter in unmanagedJars) map { (config, base, filter, excl) =>
			(base * (filter -- excl) +++ (base / config.name).descendentsExcept(filter, excl)).classpath
		}
	)
	def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
	lazy val defaultPackages: Seq[TaskKey[File]] =
		for(task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
	lazy val defaultArtifactTasks: Seq[TaskKey[File]] = makePom +: defaultPackages

	def findClasspathConfig(map: Configuration => Configuration, thisConfig: Configuration, delegate: Task[Option[Configuration]], up: Task[UpdateReport]): Task[Configuration] =
		(delegate :^: up :^: KNil) map { case delegated :+: report :+: HNil =>
			val defined = report.allConfigurations.toSet
			val search = map(thisConfig) +: (delegated.toList ++ Seq(Compile, Configurations.Default))
			def notFound = error("Configuration to use for managed classpath must be explicitly defined when default configurations are not present.")
			search find { defined contains _.name } getOrElse notFound
		}

	def packaged(pkgTasks: Seq[TaskKey[File]]): Initialize[Task[Map[Artifact, File]]] =
		enabledOnly(packagedArtifact.task, pkgTasks) apply (_.join.map(_.toMap))
	def artifactDefs(pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[Artifact]] =
		enabledOnly(artifact, pkgTasks)

	def enabledOnly[T](key: SettingKey[T], pkgTasks: Seq[TaskKey[File]]): Initialize[Seq[T]] =
		( forallIn(key, pkgTasks) zipWith forallIn(publishArtifact, pkgTasks) ) ( _ zip _ collect { case (a, true) => a } )
	def forallIn[T](key: SettingKey[T], pkgTasks: Seq[TaskKey[_]]): Initialize[Seq[T]] =
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
	val baseSettings: Seq[Setting[_]] = sbtClassifiersTasks ++ Seq(
		conflictWarning in GlobalScope :== ConflictWarning.default("global"),
		conflictWarning <<= (thisProjectRef, conflictWarning) { (ref, cw) => cw.copy(label = Project.display(ref)) },
		unmanagedBase <<= baseDirectory / "lib",
		normalizedName <<= name(StringUtilities.normalize),
		isSnapshot <<= isSnapshot or version(_ endsWith "-SNAPSHOT"),
		description <<= description or name,
		homepage in GlobalScope :== None,
		startYear in GlobalScope :== None,
		licenses in GlobalScope :== Nil,
		organization <<= organization or normalizedName,
		organizationName <<= organizationName or organization,
		organizationHomepage <<= organizationHomepage or homepage,
		projectInfo <<= (name, description, homepage, startYear, licenses, organizationName, organizationHomepage) apply ModuleInfo,
		externalResolvers <<= (externalResolvers.task.?, resolvers) {
			case (Some(delegated), Seq()) => delegated
			case (_, rs) => task { Resolver.withDefaultResolvers(rs) }
		},
		fullResolvers <<= (projectResolver,externalResolvers,sbtPlugin,sbtResolver) map { (pr,rs,isPlugin,sr) =>
			val base = pr +: rs
			if(isPlugin) sr +: base else base
		},
		offline in GlobalScope :== false,
		moduleName <<= normalizedName,
		defaultConfiguration in GlobalScope :== Some(Configurations.Compile),
		defaultConfigurationMapping in GlobalScope <<= defaultConfiguration{ case Some(d) => "*->" + d.name; case None => "*->*" },
		ivyPaths <<= (baseDirectory, appConfiguration) { (base, app) => new IvyPaths(base, bootIvyHome(app)) },
		otherResolvers <<= publishTo(_.toList),
		projectResolver <<= projectResolverTask,
		projectDependencies <<= projectDependenciesTask,
		libraryDependencies in GlobalScope :== Nil,
		libraryDependencies <++= (autoScalaLibrary, sbtPlugin, scalaVersion) apply autoLibraryDependency,
		allDependencies <<= (projectDependencies,libraryDependencies,sbtPlugin,sbtDependency) map { (projDeps, libDeps, isPlugin, sbtDep) =>
			val base = projDeps ++ libDeps
			if(isPlugin) sbtDep.copy(configurations = Some(Provided.name)) +: base else base
		},
		ivyLoggingLevel in GlobalScope :== UpdateLogging.Quiet,
		ivyXML in GlobalScope :== NodeSeq.Empty,
		ivyValidate in GlobalScope :== false,
		ivyScala <<= ivyScala or (scalaHome, scalaVersion, scalaVersion in update) { (sh,v,vu) =>
			Some(new IvyScala(v, Nil, filterImplicit = true, checkExplicit = true, overrideScalaVersion = sh.isEmpty, substituteCross = x => IvySbt.substituteCross(x, vu)))
		},
		moduleConfigurations in GlobalScope :== Nil,
		publishTo in GlobalScope :== None,
		artifactPath in makePom <<= artifactPathSetting(artifact in makePom),
		publishArtifact in makePom <<= publishMavenStyle,
		artifact in makePom <<= moduleName(Artifact.pom),
		projectID <<= (organization,moduleName,version,artifacts,crossPaths){ (org,module,version,as,crossEnabled) =>
			ModuleID(org, module, version).cross(crossEnabled).artifacts(as : _*)
		},
		projectID <<= pluginProjectID,
		resolvers in GlobalScope :== Nil,
		projectDescriptors <<= depMap,
		retrievePattern in GlobalScope :== Resolver.defaultRetrievePattern,
		updateConfiguration <<= (retrieveConfiguration, ivyLoggingLevel)((conf,level) => new UpdateConfiguration(conf, false, level) ),
		retrieveConfiguration <<= (managedDirectory, retrievePattern, retrieveManaged) { (libm, pattern, enabled) => if(enabled) Some(new RetrieveConfiguration(libm, pattern)) else None },
		ivyConfiguration <<= mkIvyConfiguration,
		ivyConfigurations <<= (autoCompilerPlugins, internalConfigurationMap, thisProject) { (auto, internalMap, project) =>
			(project.configurations ++ project.configurations.map(internalMap) ++ (if(auto) CompilerPlugin :: Nil else Nil)).distinct
		},
		ivyConfigurations ++= Configurations.auxiliary,
		moduleSettings <<= moduleSettings0,
		makePomConfiguration <<= (artifactPath in makePom, projectInfo, pomExtra, pomPostProcess, pomIncludeRepository, pomAllRepositories) {
			(file, minfo, extra, process, include, all) => new MakePomConfiguration(file, minfo, None, extra, process, include, all)
		},
		deliverLocalConfiguration <<= (crossTarget, ivyLoggingLevel) map { (outDir, level) => deliverConfig( outDir, logging = level ) },
		deliverConfiguration <<= deliverLocalConfiguration,
		publishConfiguration <<= (packagedArtifacts, publishTo, publishMavenStyle, deliver, checksums in publish, ivyLoggingLevel) map { (arts, publishTo, mavenStyle, ivyFile, checks, level) =>
			publishConfig(arts, if(mavenStyle) None else Some(ivyFile), resolverName = getPublishTo(publishTo).name, checksums = checks, logging = level)
		},
		publishLocalConfiguration <<= (packagedArtifacts, deliverLocal, checksums in publishLocal, ivyLoggingLevel) map {
			(arts, ivyFile, checks, level) => publishConfig(arts, Some(ivyFile), checks, logging = level )
		},
		ivySbt <<= ivySbt0,
		ivyModule <<= (ivySbt, moduleSettings) map { (ivySbt, settings) => new ivySbt.Module(settings) },
		transitiveUpdate <<= transitiveUpdateTask,
		update <<= (ivyModule, thisProjectRef, updateConfiguration, cacheDirectory, scalaInstance, transitiveUpdate, streams) map { (module, ref, config, cacheDirectory, si, reports, s) =>
			val depsUpdated = reports.exists(!_.stats.cached)
			cachedUpdate(cacheDirectory / "update", Project.display(ref), module, config, Some(si), depsUpdated, s.log)
		},
		update <<= (conflictWarning, update, streams) map { (config, report, s) => ConflictWarning(config, report, s.log); report },
		transitiveClassifiers in GlobalScope :== Seq(SourceClassifier, DocClassifier),
		classifiersModule in updateClassifiers <<= (projectID, update, transitiveClassifiers in updateClassifiers, ivyConfigurations in updateClassifiers) map { ( pid, up, classifiers, confs) =>
			GetClassifiersModule(pid, up.allModules, confs, classifiers)
		},
		updateClassifiers <<= (ivySbt, classifiersModule in updateClassifiers, updateConfiguration, ivyScala, target in LocalRootProject, appConfiguration, streams) map { (is, mod, c, ivyScala, out, app, s) =>
			withExcludes(out, mod.classifiers, lock(app)) { excludes =>
				IvyActions.updateClassifiers(is, GetClassifiersConfiguration(mod, excludes, c, ivyScala), s.log)
			}
		},
		sbtDependency in GlobalScope <<= appConfiguration { app =>
			val id = app.provider.id
			val base = ModuleID(id.groupID, id.name, id.version, crossVersion = id.crossVersioned)
			IvySbt.substituteCross(base, app.provider.scalaProvider.version).copy(crossVersion = false)
		}
	)
	def pluginProjectID: Initialize[ModuleID] = (sbtVersion in update, scalaVersion, projectID, sbtPlugin) { (sbtV, scalaV, pid, isPlugin) =>
		if(isPlugin) sbtPluginExtra(pid, sbtV, scalaV) else pid
	}
	def ivySbt0: Initialize[Task[IvySbt]] =
		(ivyConfiguration, credentials, streams) map { (conf, creds, s) =>
			Credentials.register(creds, s.log)
			new IvySbt(conf)
		}
	def moduleSettings0: Initialize[Task[ModuleSettings]] =
		(projectID, allDependencies, ivyXML, ivyConfigurations, defaultConfiguration, ivyScala, ivyValidate, projectInfo) map {
			(pid, deps, ivyXML, confs, defaultConf, ivyS, validate, pinfo) => new InlineConfiguration(pid, pinfo, deps, ivyXML, confs, defaultConf, ivyS, validate)
		}
		
	def sbtClassifiersTasks = inTask(updateSbtClassifiers)(Seq(
		transitiveClassifiers in GlobalScope in updateSbtClassifiers ~= ( _.filter(_ != DocClassifier) ),
		externalResolvers <<= (externalResolvers, appConfiguration) map { (defaultRs, ac) =>
			bootRepositories(ac) getOrElse defaultRs
		},
		ivyConfiguration <<= (externalResolvers, ivyPaths, offline, checksums, appConfiguration, streams) map { (rs, paths, off, check, app, s) =>
			new InlineIvyConfiguration(paths, rs, Nil, Nil, off, Option(lock(app)), check, s.log)
		},
		ivySbt <<= ivySbt0,
		classifiersModule <<= (projectID, sbtDependency, transitiveClassifiers, loadedBuild, thisProjectRef) map { ( pid, sbtDep, classifiers, lb, ref) =>
			val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
			GetClassifiersModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil, classifiers)
		},
		updateSbtClassifiers in TaskGlobal <<= (ivySbt, classifiersModule, updateConfiguration, ivyScala, target in LocalRootProject, appConfiguration, streams) map {
				(is, mod, c, ivyScala, out, app, s) =>
			withExcludes(out, mod.classifiers, lock(app)) { excludes =>
				IvyActions.transitiveScratch(is, "sbt", GetClassifiersConfiguration(mod, excludes, c, ivyScala), s.log)
			}
		}
	))

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

	def cachedUpdate(cacheFile: File, label: String, module: IvySbt#Module, config: UpdateConfiguration, scalaInstance: Option[ScalaInstance], depsUpdated: Boolean, log: Logger): UpdateReport =
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
		def uptodate(inChanged: Boolean, out: UpdateReport): Boolean =
			!depsUpdated &&
			!inChanged &&
			out.allFiles.forall(_.exists) &&
			out.cachedDescriptor.exists

		val f =
			Tracked.inputChanged(cacheFile / "inputs") { (inChanged: Boolean, in: In) =>
				val outCache = Tracked.lastOutput[In, UpdateReport](cacheFile / "output") {
					case (_, Some(out)) if uptodate(inChanged, out) => out
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
	def publishConfig(artifacts: Map[Artifact, File], ivyFile: Option[File], checksums: Seq[String], resolverName: String = "local", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
	    new PublishConfiguration(ivyFile, resolverName, artifacts, checksums, logging)

	def deliverPattern(outputPath: File): String  =  (outputPath / "[artifact]-[revision](-[classifier]).[ext]").absolutePath

	def projectDependenciesTask: Initialize[Task[Seq[ModuleID]]] =
		(thisProjectRef, settings, buildDependencies) map { (ref, data, deps) =>
			deps.classpath(ref) flatMap { dep => (projectID in dep.project) get data map { _.copy(configurations = dep.configuration) } }
		}

	def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
		(thisProjectRef, settings, buildDependencies, streams) flatMap { (root, data, deps, s) =>
			depMap(deps classpathTransitiveRefs root, data, s.log)
		}

	def depMap(projects: Seq[ProjectRef], data: Settings[Scope], log: Logger): Task[Map[ModuleRevisionId, ModuleDescriptor]] =
		projects.flatMap( ivyModule in _ get data).join.map { mod =>
			mod map { _.dependencyMapping(log) } toMap ;
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

	def constructBuildDependencies: Initialize[BuildDependencies] =
		loadedBuild { lb =>
				import collection.mutable.HashMap
			val agg = new HashMap[ProjectRef, Seq[ProjectRef]]
			val cp = new HashMap[ProjectRef, Seq[ClasspathDep[ProjectRef]]]
			for(lbu <- lb.units.values; rp <- lbu.defined.values)
			{
				val ref = ProjectRef(lbu.unit.uri, rp.id)
				cp(ref) = rp.dependencies
				agg(ref) = rp.aggregate
			}
			BuildDependencies(cp.toMap, agg.toMap)
		}
	def internalDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, classpathConfiguration, configuration, settings, buildDependencies) flatMap internalDependencies0
	def unmanagedDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, configuration, settings, buildDependencies) flatMap unmanagedDependencies0
	def mkIvyConfiguration: Initialize[Task[IvyConfiguration]] =
		(fullResolvers, ivyPaths, otherResolvers, moduleConfigurations, offline, checksums in update, appConfiguration, streams) map { (rs, paths, other, moduleConfs, off, check, app, s) =>
			new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, Some(lock(app)), check, s.log)
		}

		import java.util.LinkedHashSet
		import collection.JavaConversions.asScalaSet
	def interSort(projectRef: ProjectRef, conf: Configuration, data: Settings[Scope], deps: BuildDependencies): Seq[(ProjectRef,String)] =
	{
		val visited = asScalaSet(new LinkedHashSet[(ProjectRef,String)])
		def visit(p: ProjectRef, c: Configuration)
		{
			val applicableConfigs = allConfigs(c)
			for(ac <- applicableConfigs) // add all configurations in this project
				visited add (p, ac.name)
			val masterConfs = names(getConfigurations(projectRef, data))

			for( ResolvedClasspathDependency(dep, confMapping) <- deps.classpath(p))
			{
				val configurations = getConfigurations(dep, data)
				val mapping = mapped(confMapping, masterConfs, names(configurations), "compile", "*->compile")
				// map master configuration 'c' and all extended configurations to the appropriate dependency configuration
				for(ac <- applicableConfigs; depConfName <- mapping(ac.name))
				{
					for(depConf <- confOpt(configurations, depConfName) )
						if( ! visited( (dep, depConfName) ) )
							visit(dep, depConf)
				}
			}
		}
		visit(projectRef, conf)
		visited.toSeq
	}
	def unmanagedDependencies0(projectRef: ProjectRef, conf: Configuration, data: Settings[Scope], deps: BuildDependencies): Task[Classpath] =
		interDependencies(projectRef, deps, conf, conf, data, true, unmanagedLibs)
	def internalDependencies0(projectRef: ProjectRef, conf: Configuration, self: Configuration, data: Settings[Scope], deps: BuildDependencies): Task[Classpath] =
		interDependencies(projectRef, deps, conf, self, data, false, productsTask)
	def interDependencies(projectRef: ProjectRef, deps: BuildDependencies, conf: Configuration, self: Configuration, data: Settings[Scope], includeSelf: Boolean,
		f: (ProjectRef, String, Settings[Scope]) => Task[Classpath]): Task[Classpath] =
	{
		val visited = interSort(projectRef, conf, data, deps)
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
		( key in (dep, ConfigKey(conf)) ) get data getOrElse constant(Nil)
	def defaultConfigurationTask(p: ResolvedReference, data: Settings[Scope]): Configuration =
		flatten(defaultConfiguration in p get data) getOrElse Configurations.Default
	def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap idFun

	lazy val typesafeSnapshots = typesafeRepo("snapshots")
	lazy val typesafeResolver = typesafeRepo("releases")
	def typesafeRepo(status: String) = Resolver.url("typesafe-ivy-"+status, new URL("http://repo.typesafe.com/typesafe/ivy-" + status + "/"))(Resolver.ivyStylePatterns)

	def modifyForPlugin(plugin: Boolean, dep: ModuleID): ModuleID =
		if(plugin) dep.copy(configurations = Some(Provided.name)) else dep
	def autoLibraryDependency(auto: Boolean, plugin: Boolean, version: String): Seq[ModuleID] =
		if(auto)
			modifyForPlugin(plugin, ScalaArtifacts.libraryDependency(version)) :: Nil
		else
			Nil

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

	def bootChecksums(app: xsbti.AppConfiguration): Seq[String] =
		try { app.provider.scalaProvider.launcher.checksums.toSeq }
		catch { case _: NoSuchMethodError => IvySbt.DefaultChecksums }

	def bootRepositories(app: xsbti.AppConfiguration): Option[Seq[Resolver]] =
		try { Some(app.provider.scalaProvider.launcher.ivyRepositories.toSeq map bootRepository) }
		catch { case _: NoSuchMethodError => None }
	private[this] def bootRepository(repo: xsbti.Repository): Resolver =
	{
		import xsbti.Predefined
		repo match
		{
			case m: xsbti.MavenRepository => MavenRepository(m.id, m.url.toString)
			case i: xsbti.IvyRepository => Resolver.url(i.id, i.url)(Patterns(i.ivyPattern :: Nil, i.artifactPattern :: Nil, false))
			case p: xsbti.PredefinedRepository => p.id match {
				case Predefined.Local => Resolver.defaultLocal
				case Predefined.MavenLocal => Resolver.mavenLocal
				case Predefined.MavenCentral => DefaultMavenRepository
				case Predefined.ScalaToolsReleases => ScalaToolsReleases
				case Predefined.ScalaToolsSnapshots => ScalaToolsSnapshots
			}
		}
	}
}

trait BuildExtra extends BuildCommon
{
		import Defaults._

	def addSbtPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
		libraryDependencies <+= (sbtVersion in update,scalaVersion) { (sbtV, scalaV) => sbtPluginExtra(dependency, sbtV, scalaV) }

	def compilerPlugin(dependency: ModuleID): ModuleID =
		dependency.copy(configurations = Some("plugin->default(compile)"))

	def addCompilerPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
		libraryDependencies += compilerPlugin(dependency)

	def addArtifact(a: Artifact, taskDef: TaskKey[File]): SettingsDefinition =
	{
		val pkgd = packagedArtifacts <<= (packagedArtifacts, taskDef) map ( (pas,file) => pas updated (a, file) )
		seq( artifacts += a, pkgd )
	}
	def addArtifact(artifact: Initialize[Artifact], taskDef: Initialize[Task[File]]): SettingsDefinition =
	{
		val artLocal = SettingKey.local[Artifact]
		val taskLocal = TaskKey.local[File]
		val art = artifacts <<= (artLocal, artifacts)( _ +: _ )
		val pkgd = packagedArtifacts <<= (packagedArtifacts, artLocal, taskLocal) map ( (pas,a,file) => pas updated (a, file))
		seq( artLocal <<= artifact, taskLocal <<= taskDef, art, pkgd )
	}

	def seq(settings: Setting[_]*): SettingsDefinition = new Project.SettingList(settings)

	def externalIvySettings(file: Initialize[File] = baseDirectory / "ivysettings.xml"): Setting[Task[IvyConfiguration]] =
	{
		val other = (baseDirectory, appConfiguration, streams).identityMap
		ivyConfiguration <<= (file zipWith other) { case (f, otherTask) =>
			otherTask map { case (base, app, s) => new ExternalIvyConfiguration(base, f, Some(lock(app)), s.log) }
		}
	}
	def externalIvyFile(file: Initialize[File] = baseDirectory / "ivy.xml", iScala: Initialize[Option[IvyScala]] = ivyScala): Setting[Task[ModuleSettings]] =
		external(file, iScala)( (f, is, v) => new IvyFileConfiguration(f, is, v) ) 
	def externalPom(file: Initialize[File] = baseDirectory / "pom.xml", iScala: Initialize[Option[IvyScala]] = ivyScala): Setting[Task[ModuleSettings]] =
		external(file, iScala)( (f, is, v) => new PomConfiguration(f, is, v) )

	private[this] def external(file: Initialize[File], iScala: Initialize[Option[IvyScala]])(make: (File, Option[IvyScala], Boolean) => ModuleSettings): Setting[Task[ModuleSettings]] =
		moduleSettings <<= ((file zip iScala) zipWith ivyValidate) { case ((f, is), v) => task { make(f, is, v) } }

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
	
	def fullRunInputTask(scoped: InputKey[Unit], config: Configuration, mainClass: String, baseArguments: String*): Setting[InputTask[Unit]] =
		scoped <<= inputTask { result =>
			( initScoped(scoped.scopedKey, runnerInit) zipWith (fullClasspath in config, streams, result).identityMap) { (rTask, t) =>
				(t :^: rTask :^: KNil) map { case (cp, s, args) :+: r :+: HNil =>
					toError(r.run(mainClass, data(cp), baseArguments ++ args, s.log))
				}
			}
		}
	def fullRunTask(scoped: TaskKey[Unit], config: Configuration, mainClass: String, arguments: String*): Setting[Task[Unit]] =
		scoped <<= ( initScoped(scoped.scopedKey, runnerInit) zipWith (fullClasspath in config, streams).identityMap ) { case (rTask, t) =>
			(t :^: rTask :^: KNil) map { case (cp, s) :+: r :+: HNil =>
				toError(r.run(mainClass, data(cp), arguments, s.log))
			}
		}
	def initScoped[T](sk: ScopedKey[_], i: Initialize[T]): Initialize[T]  =  initScope(fillTaskAxis(sk.scope, sk.key), i)
	def initScope[T](s: Scope, i: Initialize[T]): Initialize[T]  =  i mapReferenced Project.mapScope(Scope.replaceThis(s))
	
	/** Disables post-compilation hook for determining tests for tab-completion (such as for 'test-only').
	* This is useful for reducing test:compile time when not running test. */
	def noTestCompletion(config: Configuration = Test): Setting[_]  =  inConfig(config)( Seq(definedTests <<= Defaults.detectTests) ).head

	def filterKeys(ss: Seq[Setting[_]], transitive: Boolean = false)(f: ScopedKey[_] => Boolean): Seq[Setting[_]] =
		ss filter ( s => f(s.key) && (!transitive || s.dependencies.forall(f)) )
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

	def overrideConfigs(cs: Configuration*)(configurations: Seq[Configuration]): Seq[Configuration] =
	{
		val existingName = configurations.map(_.name).toSet
		val newByName = cs.map(c => (c.name, c)).toMap
		val overridden = configurations map { conf => newByName.getOrElse(conf.name, conf) }
		val newConfigs = cs filter { c => !existingName(c.name) }
		overridden ++ newConfigs
	}

		// these are intended for use in input tasks for creating parsers
	def getFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State): Option[T] =
		SessionVar.get(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

	def loadFromContext[T](task: TaskKey[T], context: ScopedKey[_], s: State)(implicit f: sbinary.Format[T]): Option[T] =
		SessionVar.load(SessionVar.resolveContext(task.scopedKey, context.scope, s), s)

		// intended for use in constructing InputTasks
	def loadForParser[P,T](task: TaskKey[T])(f: (State, Option[T]) => Parser[P])(implicit format: sbinary.Format[T]): Initialize[State => Parser[P]] =
		loadForParserI(task)(Project value f)(format)
	def loadForParserI[P,T](task: TaskKey[T])(init: Initialize[(State, Option[T]) => Parser[P]])(implicit format: sbinary.Format[T]): Initialize[State => Parser[P]] =
		(resolvedScoped, init)( (ctx, f) => (s: State) => f( s, loadFromContext(task, ctx, s)(format)) )

	def getForParser[P,T](task: TaskKey[T])(init: (State, Option[T]) => Parser[P]): Initialize[State => Parser[P]] =
		getForParserI(task)(Project value init)
	def getForParserI[P,T](task: TaskKey[T])(init: Initialize[(State, Option[T]) => Parser[P]]): Initialize[State => Parser[P]] =
		(resolvedScoped, init)( (ctx, f) => (s: State) => f(s, getFromContext(task, ctx, s)) )

		// these are for use for constructing Tasks
	def loadPrevious[T](task: TaskKey[T])(implicit f: sbinary.Format[T]): Initialize[Task[Option[T]]] =
		(state, resolvedScoped) map { (s, ctx) => loadFromContext(task, ctx, s)(f) }

	def getPrevious[T](task: TaskKey[T]): Initialize[Task[Option[T]]] =
		(state, resolvedScoped) map { (s, ctx) => getFromContext(task, ctx, s) }
}