/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Attributed.data
	import Scope.{fillTaskAxis, GlobalScope, ThisScope}
	import xsbt.api.Discovery
	import xsbti.compile.CompileOrder
	import Project.{inConfig, inScope, inTask, richInitialize, richInitializeTask, richTaskSessionVar}
	import Def.{Initialize, ScopedKey, Setting, SettingsDefinition}
	import Artifact.{DocClassifier, SourceClassifier}
	import Configurations.{Compile, CompilerPlugin, IntegrationTest, names, Provided, Runtime, Test}
	import CrossVersion.{binarySbtVersion, binaryScalaVersion, partialVersion}
	import complete._
	import std.TaskExtra._
	import inc.{FileValueCache, Locate}
	import testing.{Framework, AnnotatedFingerprint, SubclassFingerprint}

	import sys.error
	import scala.xml.NodeSeq
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import java.io.{File, PrintWriter}
	import java.net.{URI,URL,MalformedURLException}
	import java.util.concurrent.Callable
	import sbinary.DefaultProtocol.StringFormat
	import Cache.seqFormat

	import Types._
	import Path._
	import Keys._

object Defaults extends BuildCommon
{
	final val CacheDirectoryName = "cache"

	def configSrcSub(key: SettingKey[File]): Initialize[File] = (key in ThisScope.copy(config = Global), configuration) { (src, conf) => src / nameForSrc(conf.name) }
	def nameForSrc(config: String) = if(config == Configurations.Compile.name) "main" else config
	def prefix(config: String) = if(config == Configurations.Compile.name) "" else config + "-"

	def lock(app: xsbti.AppConfiguration): xsbti.GlobalLock = app.provider.scalaProvider.launcher.globalLock

	def extractAnalysis[T](a: Attributed[T]): (T, inc.Analysis) =
		(a.data, a.metadata get Keys.analysis getOrElse inc.Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): T => Option[inc.Analysis] =
	{
		val m = (for(a <- cp; an <- a.metadata get Keys.analysis) yield (a.data, an) ).toMap
		m.get _
	}

	def buildCore: Seq[Setting[_]] = thisBuildCore ++ globalCore
	def thisBuildCore: Seq[Setting[_]] = inScope(GlobalScope.copy(project = Select(ThisBuild)))(Seq(
		managedDirectory := baseDirectory.value / "lib_managed"
	))
	def globalCore: Seq[Setting[_]] = inScope(GlobalScope)(defaultTestTasks(test) ++ defaultTestTasks(testOnly) ++ defaultTestTasks(testQuick) ++ Seq(
		compilerCache := state.value get Keys.stateCompilerCache getOrElse compiler.CompilerCache.fresh,
		crossVersion :== CrossVersion.Disabled,
		scalaOrganization :== ScalaArtifacts.Organization,
		buildDependencies <<= buildDependencies or Classpaths.constructBuildDependencies,
		taskTemporaryDirectory := { val dir = IO.createTemporaryDirectory; dir.deleteOnExit(); dir },
		onComplete := { val dir = taskTemporaryDirectory.value; () => IO.delete(dir); IO.createDirectory(dir) },
		concurrentRestrictions <<= concurrentRestrictions or defaultRestrictions,
		parallelExecution :== true,
		sbtVersion := appConfiguration.value.provider.id.version,
		sbtBinaryVersion := binarySbtVersion(sbtVersion.value),
		sbtResolver := { if(sbtVersion.value endsWith "-SNAPSHOT") Classpaths.typesafeSnapshots else Classpaths.typesafeReleases },
		pollInterval :== 500,
		logBuffered :== false,
		connectInput :== false,
		cancelable :== false,
		envVars :== Map.empty,
		sourcesInBase :== true,
		autoAPIMappings := false,
		apiMappings := Map.empty,
		autoScalaLibrary :== true,
		managedScalaInstance :== true,
		onLoad <<= onLoad ?? idFun[State],
		onUnload <<= (onUnload ?? idFun[State]),
		onUnload := { s => try onUnload.value(s) finally IO.delete(taskTemporaryDirectory.value) },
		watchingMessage <<= watchingMessage ?? Watched.defaultWatchingMessage,
		triggeredMessage <<= triggeredMessage ?? Watched.defaultTriggeredMessage,
		definesClass :== FileValueCache(Locate.definesClass _ ).get,
		trapExit :== false,
		trapExit in run :== true,
		traceLevel in run :== 0,
		traceLevel in runMain :== 0,
		traceLevel in console :== Int.MaxValue,
		traceLevel in consoleProject :== Int.MaxValue,
		autoCompilerPlugins :== true,
		internalConfigurationMap :== Configurations.internalMap _,
		initialize :== {},
		credentials :== Nil,
		scalaHome :== None,
		apiURL := None,
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
		classpathTypes :== Set("jar", "bundle") ++ CustomPomParser.JarPackagings,
		aggregate :== true,
		maxErrors :== 100,
		sourcePositionMappers :== Nil,
		showTiming :== true,
		timingFormat :== Aggregation.defaultFormat,
		showSuccess :== true,
		commands :== Nil,
		retrieveManaged :== false,
		buildStructure := Project.structure(state.value),
		settingsData := buildStructure.value.data,
		artifactClassifier :== None,
		artifactClassifier in packageSrc :== Some(SourceClassifier),
		artifactClassifier in packageDoc :== Some(DocClassifier),
		checksums := Classpaths.bootChecksums(appConfiguration.value),
		conflictManager := ConflictManager.default,
		pomExtra :== NodeSeq.Empty,
		pomPostProcess :== idFun,
		pomAllRepositories :== false,
		includeFilter :== NothingFilter,
		includeFilter in unmanagedSources :== "*.java" | "*.scala",
		includeFilter in unmanagedJars :== "*.jar" | "*.so" | "*.dll" | "*.jnilib",
		includeFilter in unmanagedResources :== AllPassFilter,
		excludeFilter :== (".*"  - ".") || HiddenFileFilter,
		pomIncludeRepository :== Classpaths.defaultRepositoryFilter
	))
	def defaultTestTasks(key: Scoped): Seq[Setting[_]] = inTask(key)(Seq(
		tags := Seq(Tags.Test -> 1),
		logBuffered := true
	))
	def projectCore: Seq[Setting[_]] = Seq(
		name := thisProject.value.id,
		logManager := LogManager.defaults(extraLoggers.value, StandardMain.console),
		onLoadMessage <<= onLoadMessage or (name, thisProjectRef)("Set current project to " + _ + " (in build " + _.build +")"),
		runnerTask
	)
	def paths = Seq(
		baseDirectory := thisProject.value.base,
		target := baseDirectory.value / "target",
		historyPath <<= historyPath or target(t => Some(t / ".history")),
		sourceDirectory := baseDirectory.value / "src",
		sourceManaged := crossTarget.value / "src_managed",
		resourceManaged := crossTarget.value / "resource_managed",
		cacheDirectory := crossTarget.value / CacheDirectoryName / thisProject.value.id / "global"
	)

	lazy val configPaths = sourceConfigPaths ++ resourceConfigPaths ++ outputConfigPaths
	lazy val sourceConfigPaths = Seq(
		sourceDirectory <<= configSrcSub(sourceDirectory),
		sourceManaged <<= configSrcSub(sourceManaged),
		scalaSource := sourceDirectory.value / "scala",
		javaSource := sourceDirectory.value / "java",
		unmanagedSourceDirectories := Seq(scalaSource.value, javaSource.value),
		unmanagedSources <<= collectFiles(unmanagedSourceDirectories, includeFilter in unmanagedSources, excludeFilter in unmanagedSources),
		watchSources in ConfigGlobal <++= unmanagedSources,
		managedSourceDirectories := Seq(sourceManaged.value),
		managedSources <<= generate(sourceGenerators),
		sourceGenerators :== Nil,
		sourceDirectories <<= Classpaths.concatSettings(unmanagedSourceDirectories, managedSourceDirectories),
		sources <<= Classpaths.concat(unmanagedSources, managedSources)
	)
	lazy val resourceConfigPaths = Seq(
		resourceDirectory := sourceDirectory.value / "resources",
		resourceManaged <<= configSrcSub(resourceManaged),
		unmanagedResourceDirectories := Seq(resourceDirectory.value),
		managedResourceDirectories := Seq(resourceManaged.value),
		resourceDirectories <<= Classpaths.concatSettings(unmanagedResourceDirectories, managedResourceDirectories),
		unmanagedResources <<= collectFiles(unmanagedResourceDirectories, includeFilter in unmanagedResources, excludeFilter in unmanagedResources),
		watchSources in ConfigGlobal ++= unmanagedResources.value,
		resourceGenerators :== Nil,
		resourceGenerators <+= (definedSbtPlugins, resourceManaged) map writePluginsDescriptor,
		managedResources <<= generate(resourceGenerators),
		resources <<= Classpaths.concat(managedResources, unmanagedResources)
	)
	lazy val outputConfigPaths = Seq(
		cacheDirectory := crossTarget.value / CacheDirectoryName / thisProject.value.id / configuration.value.name,
		classDirectory := crossTarget.value / (prefix(configuration.value.name) + "classes"),
		target in doc := crossTarget.value / (prefix(configuration.value.name) + "api")
	)
	def addBaseSources = Seq(
		unmanagedSources := {
			val srcs = unmanagedSources.value
			val f = (includeFilter in unmanagedSources).value
			val excl = (excludeFilter in unmanagedSources).value
			if(sourcesInBase.value) (srcs +++ baseDirectory.value * (f -- excl)).get else srcs
		}
	)

	def compileBase = inTask(console)(compilersSetting :: Nil) ++ Seq(
		classpathOptions in GlobalScope :== ClasspathOptions.boot,
		classpathOptions in GlobalScope in console :== ClasspathOptions.repl,
		compileOrder in GlobalScope :== CompileOrder.Mixed,
		compilersSetting,
		javacOptions in GlobalScope :== Nil,
		scalacOptions in GlobalScope :== Nil,
		incOptions in GlobalScope := sbt.inc.IncOptions.defaultTransactional(crossTarget.value.getParentFile / "classes.bak"),
		scalaInstance <<= scalaInstanceTask,
		scalaVersion in GlobalScope := appConfiguration.value.provider.scalaProvider.version,
		scalaBinaryVersion in GlobalScope := binaryScalaVersion(scalaVersion.value),
		crossVersion := (if(crossPaths.value) CrossVersion.binary else CrossVersion.Disabled),
		crossScalaVersions in GlobalScope := Seq(scalaVersion.value),
		crossTarget := makeCrossTarget(target.value, scalaBinaryVersion.value, sbtBinaryVersion.value, sbtPlugin.value, crossPaths.value)
	)
	def makeCrossTarget(t: File, sv: String, sbtv: String, plugin: Boolean, cross: Boolean): File =
	{
		val scalaBase = if(cross) t / ("scala-" + sv) else t
		if(plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
	}
	def compilersSetting = compilers := Compiler.compilers(scalaInstance.value, classpathOptions.value, javaHome.value)(appConfiguration.value, streams.value.log)

	lazy val configTasks = docTaskSettings(doc) ++ compileTaskSettings ++ compileInputsSettings ++ Seq(
		initialCommands in GlobalScope :== "",
		cleanupCommands in GlobalScope :== "",
		compile <<= compileTask tag(Tags.Compile, Tags.CPU),
		printWarnings <<= printWarningsTask,
		compileIncSetup <<= compileIncSetupTask,
		console <<= consoleTask,
		consoleQuick <<= consoleQuickTask,
		discoveredMainClasses <<= compile map discoverMainClasses storeAs discoveredMainClasses triggeredBy compile,
		definedSbtPlugins <<= discoverPlugins,
		inTask(run)(runnerTask :: Nil).head,
		selectMainClass := mainClass.value orElse selectRunMain(discoveredMainClasses.value),
		mainClass in run := (selectMainClass in run).value,
		mainClass := selectPackageMain(discoveredMainClasses.value),
		run <<= runTask(fullClasspath, mainClass in run, runner in run),
		runMain <<= runMainTask(fullClasspath, runner in run),
		copyResources <<= copyResourcesTask
	)

	lazy val projectTasks: Seq[Setting[_]] = Seq(
		cleanFiles := Seq(managedDirectory.value, target.value),
		cleanKeepFiles := historyPath.value.toList,
		clean := doClean(cleanFiles.value, cleanKeepFiles.value),
		consoleProject <<= consoleProjectTask,
		watchTransitiveSources <<= watchTransitiveSourcesTask,
		watch <<= watchSetting
	)

	def generate(generators: SettingKey[Seq[Task[Seq[File]]]]): Initialize[Task[Seq[File]]] = generators {_.join.map(_.flatten) }

	@deprecated("Use the new <key>.all(<ScopeFilter>) API", "0.13.0")
	def inAllConfigurations[T](key: TaskKey[T]): Initialize[Task[Seq[T]]] = (state, thisProjectRef) flatMap { (state, ref) =>
		val structure = Project structure state
		val configurations = Project.getProject(ref, structure).toList.flatMap(_.configurations)
		configurations.flatMap { conf =>
			key in (ref, conf) get structure.data
		} join
	}
	def watchTransitiveSourcesTask: Initialize[Task[Seq[File]]] = {
			import ScopeFilter.Make.{inDependencies => inDeps, _}
		val selectDeps = ScopeFilter(inAggregates(ThisProject) || inDeps(ThisProject))
		val allWatched = (watchSources ?? Nil).all( selectDeps )
		Def.task { allWatched.value.flatten }
	}

	def transitiveUpdateTask: Initialize[Task[Seq[UpdateReport]]] = {
			import ScopeFilter.Make.{inDependencies => inDeps, _}
		val selectDeps = ScopeFilter(inDeps(ThisProject, includeRoot = false))
		val allUpdates = update.?.all(selectDeps)
		Def.task { allUpdates.value.flatten }
	}

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
				case None => error("key not found: " + Def.displayFull(key))
			}
		}
	}

	@deprecated("Use scalaInstanceTask.", "0.13.0")
	def scalaInstanceSetting = scalaInstanceTask
	def scalaInstanceTask: Initialize[Task[ScalaInstance]] = Def.taskDyn {
		// if this logic changes, ensure that `unmanagedScalaInstanceOnly` and `update` are changed
		//  appropriately to avoid cycles
		scalaHome.value match {
			case Some(h) => scalaInstanceFromHome(h)
			case None =>
				val scalaProvider = appConfiguration.value.provider.scalaProvider
				val version = scalaVersion.value
				if(version == scalaProvider.version) // use the same class loader as the Scala classes used by sbt
					Def.task( ScalaInstance(version, scalaProvider) )
				else
					scalaInstanceFromUpdate
		}
	}
	// Returns the ScalaInstance only if it was not constructed via `update`
	//  This is necessary to prevent cycles between `update` and `scalaInstance`
	private[sbt] def unmanagedScalaInstanceOnly: Initialize[Task[Option[ScalaInstance]]] = Def.taskDyn {
		if(scalaHome.value.isDefined) Def.task(Some(scalaInstance.value)) else Def.task(None)
	}

	private[this] def noToolConfiguration(autoInstance: Boolean): String =
	{
		val pre = "Missing Scala tool configuration from the 'update' report.  "
		val post =
			if(autoInstance)
				"'scala-tool' is normally added automatically, so this may indicate a bug in sbt or you may be removing it from ivyConfigurations, for example."
			else
				"Explicitly define scalaInstance or scalaHome or include Scala dependencies in the 'scala-tool' configuration."
		pre + post
	}

	def scalaInstanceFromUpdate: Initialize[Task[ScalaInstance]] = Def.task {
		val toolReport = update.value.configuration(Configurations.ScalaTool.name) getOrElse
			error(noToolConfiguration(managedScalaInstance.value))
		def files(id: String) =
			for { m <- toolReport.modules if m.module.name == id;
				(art, file) <- m.artifacts if art.`type` == Artifact.DefaultType }
			yield file
		def file(id: String) = files(id).headOption getOrElse error(s"Missing ${id}.jar")
		val allFiles = toolReport.modules.flatMap(_.artifacts.map(_._2))
		val libraryJar = file(ScalaArtifacts.LibraryID)
		val compilerJar = file(ScalaArtifacts.CompilerID)
		val otherJars = allFiles.filterNot(x => x ==  libraryJar || x == compilerJar)
		ScalaInstance(scalaVersion.value, libraryJar, compilerJar, otherJars : _*)(makeClassLoader(state.value))
	}
	def scalaInstanceFromHome(dir: File): Initialize[Task[ScalaInstance]] = Def.task {
		ScalaInstance(dir)(makeClassLoader(state.value))
	}
	private[this] def makeClassLoader(state: State) = state.classLoaderCache.apply _

	lazy val testTasks: Seq[Setting[_]] = testTaskOptions(test) ++ testTaskOptions(testOnly) ++ testTaskOptions(testQuick) ++ Seq(
		testLoader := TestFramework.createTestLoader(data(fullClasspath.value), scalaInstance.value, IO.createUniqueDirectory(taskTemporaryDirectory.value)),
		testFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs2, Specs, ScalaTest, JUnit)
		},
		loadedTestFrameworks := testFrameworks.value.flatMap(f => f.create(testLoader.value, streams.value.log).map( x => (f,x)).toIterable).toMap,
		definedTests <<= detectTests,
		definedTestNames <<= definedTests map ( _.map(_.name).distinct) storeAs definedTestNames triggeredBy compile,
		testListeners in GlobalScope :== Nil,
		testOptions in GlobalScope :== Nil,
		testFilter in testOnly :== (selectedFilter _),
		testFilter in testQuick <<= testQuickFilter,
		executeTests <<= (streams in test, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test) flatMap allTestGroupsTask,
		test := {
			implicit val display = Project.showContextKey(state.value)
			Tests.showResults(streams.value.log, executeTests.value, noTestsMessage(resolvedScoped.value))
		},
		testOnly <<= inputTests(testOnly),
		testQuick <<= inputTests(testQuick)
	)
	private[this] def noTestsMessage(scoped: ScopedKey[_])(implicit display: Show[ScopedKey[_]]): String =
		"No tests to run for " + display(scoped)

	lazy val TaskGlobal: Scope = ThisScope.copy(task = Global)
	lazy val ConfigGlobal: Scope = ThisScope.copy(config = Global)
	def testTaskOptions(key: Scoped): Seq[Setting[_]] = inTask(key)( Seq(
		testListeners := {
			TestLogger(streams.value.log, testLogger(streamsManager.value, test in resolvedScoped.value.scope), logBuffered.value) +:
			new TestStatusReporter(succeededFile( streams.in(test).value.cacheDirectory )) +:
			testListeners.in(TaskGlobal).value
		},
		testOptions := Tests.Listeners(testListeners.value) +: (testOptions in TaskGlobal).value,
		testExecution <<= testExecutionTask(key),
		testGrouping <<= testGrouping or singleTestGroup(key)
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
	def singleTestGroup(key: Scoped): Initialize[Task[Seq[Tests.Group]]] = Def.task {
		val tests = (definedTests in key).value
		val fk = (fork in key).value
		val opts = inTask(key, forkOptions).value
		Seq(new Tests.Group("<default>", tests, if(fk) Tests.SubProcess(opts) else Tests.InProcess))
	}
	private[this] def forkOptions: Initialize[Task[ForkOptions]] = 
		(baseDirectory, javaOptions, outputStrategy, envVars, javaHome, connectInput) map {
			(base, options, strategy, env, javaHomeDir, connectIn) =>
				// bootJars is empty by default because only jars on the user's classpath should be on the boot classpath
				ForkOptions(bootJars = Nil, javaHome = javaHomeDir, connectInput = connectIn, outputStrategy = strategy, runJVMOptions = options, workingDirectory = Some(base), envVars = env)
		}

	def testExecutionTask(task: Scoped): Initialize[Task[Tests.Execution]] =
			(testOptions in task, parallelExecution in task, tags in task) map {
				(opts, par, ts) =>
					new Tests.Execution(opts, par, ts)
			}

	def testQuickFilter: Initialize[Task[Seq[String] => Seq[String => Boolean]]] =
	  (fullClasspath in test, streams in test) map {
			(cp, s) =>
				val ans = cp.flatMap(_.metadata get Keys.analysis)
				val succeeded = TestStatus.read(succeededFile( s.cacheDirectory ))
				val stamps = collection.mutable.Map.empty[File, Long]
				def stamp(dep: String): Long = {
					val stamps = for (a <- ans; f <- a.relations.definesClass(dep)) yield intlStamp(f, a, Set.empty)
					if (stamps.isEmpty) Long.MinValue else stamps.max
				}
				def intlStamp(f: File, analysis: inc.Analysis, s: Set[File]): Long = {
					if (s contains f) Long.MinValue else
						stamps.getOrElseUpdate(f, {
							import analysis.{relations => rel, apis}
							rel.internalSrcDeps(f).map(intlStamp(_, analysis, s + f)) ++
							rel.externalDeps(f).map(stamp) +
							apis.internal(f).compilation.startTime
						}.max)
				}
				def noSuccessYet(test: String) = succeeded.get(test) match {
					case None => true
					case Some(ts) => stamp(test) > ts
				}

				args => for(filter <- selectedFilter(args)) yield
					(test: String) => filter(test) && noSuccessYet(test)
		}
	def succeededFile(dir: File) = dir / "succeeded_tests"

	def inputTests(key: InputKey[_]): Initialize[InputTask[Unit]] = inputTests0.mapReferenced(Def.mapScope(_ in key.key))
	private[this] lazy val inputTests0: Initialize[InputTask[Unit]] =
	{
		val parser = loadForParser(definedTestNames)( (s, i) => testOnlyParser(s, i getOrElse Nil) )
		Def.inputTaskDyn {
			val res = parser.parsed
			val selected = res._1
			val frameworkOptions = res._2
			val s = streams.value
			val filter = testFilter.value
			val config = testExecution.value

			implicit val display = Project.showContextKey(state.value)
			val modifiedOpts = Tests.Filters(filter(selected)) +: Tests.Argument(frameworkOptions : _*) +: config.options
			val newConfig = config.copy(options = modifiedOpts)
			val groupsTask = allTestGroupsTask(s, loadedTestFrameworks.value, testLoader.value, testGrouping.value, newConfig, fullClasspath.value, javaHome.value)
			val processed =
				for(out <- groupsTask) yield
					Tests.showResults(s.log, out, noTestsMessage(resolvedScoped.value))
			Def.value(processed)
		}
	}

	def allTestGroupsTask(s: TaskStreams, frameworks: Map[TestFramework,Framework], loader: ClassLoader, groups: Seq[Tests.Group], config: Tests.Execution,	cp: Classpath, javaHome: Option[File]): Task[Tests.Output] = {
		val groupTasks = groups map {
			case Tests.Group(name, tests, runPolicy) =>
				runPolicy match {
					case Tests.SubProcess(opts) =>
						ForkTests(frameworks.keys.toSeq, tests.toList, config, cp.files, opts, s.log) tag Tags.ForkedTestGroup
					case Tests.InProcess =>
						Tests(frameworks, loader, tests, config, s.log)
				}
		}
		Tests.foldTasks(groupTasks, config.parallel)
	}

	def selectedFilter(args: Seq[String]): Seq[String => Boolean] =
	{
		val filters = args map GlobFilter.apply
		if(filters.isEmpty)
			Seq(const(true))
		else
			filters.map { f => (s: String) => f accept s }
	}
	def detectTests: Initialize[Task[Seq[TestDefinition]]] = (loadedTestFrameworks, compile, streams) map { (frameworkMap, analysis, s) =>
		Tests.discover(frameworkMap.values.toSeq, analysis, s.log)._1
	}
	def defaultRestrictions: Initialize[Seq[Tags.Rule]] = parallelExecution { par =>
		val max = EvaluateTask.SystemProcessors
		Tags.limitAll(if(par) max else 1) :: Tags.limit(Tags.ForkedTestGroup, 1) :: Nil
	}

	lazy val packageBase: Seq[Setting[_]] = Seq(
		artifact := Artifact(moduleName.value),
		packageOptions in GlobalScope :== Nil,
		artifactName in GlobalScope :== ( Artifact.artifactName _ )
	)
	lazy val packageConfig: Seq[Setting[_]] =
		inTask(packageBin)(Seq(
			packageOptions <<= (name, version, homepage, organization, organizationName, mainClass, packageOptions) map { (name, ver, h, org, orgName, main, p) => Package.addSpecManifestAttributes(name, ver, orgName) +: Package.addImplManifestAttributes(name, ver, h, org, orgName) +: main.map(Package.MainClass.apply) ++: p })) ++
		inTask(packageSrc)(Seq(
			packageOptions := Package.addSpecManifestAttributes(name.value, version.value, organizationName.value) +: packageOptions.value )) ++
	packageTaskSettings(packageBin, packageBinMappings) ++
	packageTaskSettings(packageSrc, packageSrcMappings) ++
	packageTaskSettings(packageDoc, packageDocMappings) ++
	Seq(`package` := packageBin.value)

	def packageBinMappings = products map { _ flatMap Path.allSubpaths }
	def packageDocMappings = doc map { Path.allSubpaths(_).toSeq }
	def packageSrcMappings = concatMappings(resourceMappings, sourceMappings)

	@deprecated("Use `packageBinMappings` instead", "0.12.0")
	def packageBinTask = packageBinMappings
	@deprecated("Use `packageDocMappings` instead", "0.12.0")
	def packageDocTask = packageDocMappings
	@deprecated("Use `packageSrcMappings` instead", "0.12.0")
	def packageSrcTask = packageSrcMappings

	private type Mappings = Initialize[Task[Seq[(File, String)]]]
	def concatMappings(as: Mappings, bs: Mappings) = (as zipWith bs)( (a,b) => (a, b) map { case (a, b) => a ++ b } )

	// drop base directories, since there are no valid mappings for these
	def sourceMappings = (unmanagedSources, unmanagedSourceDirectories, baseDirectory) map { (srcs, sdirs, base) =>
		 ( (srcs --- sdirs --- base) pair (relativeTo(sdirs)|relativeTo(base)|flat)) toSeq
	}
	def resourceMappings = relativeMappings(unmanagedResources, unmanagedResourceDirectories)
	def relativeMappings(files: ScopedTaskable[Seq[File]], dirs: ScopedTaskable[Seq[File]]): Initialize[Task[Seq[(File, String)]]] =
		(files, dirs) map { (rs, rdirs) =>
			(rs --- rdirs) pair (relativeTo(rdirs)|flat) toSeq
		}

	def collectFiles(dirs: ScopedTaskable[Seq[File]], filter: ScopedTaskable[FileFilter], excludes: ScopedTaskable[FileFilter]): Initialize[Task[Seq[File]]] =
		(dirs, filter, excludes) map { (d,f,excl) => d.descendantsExcept(f,excl).get }

	def artifactPathSetting(art: SettingKey[Artifact])  =  (crossTarget, projectID, art, scalaVersion in artifactName, scalaBinaryVersion in artifactName, artifactName) {
		(t, module, a, sv, sbv, toString) =>
			t / toString(ScalaVersion(sv, sbv), module, a) asFile
	}
	def artifactSetting = ((artifact, artifactClassifier).identity zipWith configuration.?) { case ((a,classifier),cOpt) =>
		val cPart = cOpt flatMap {
			case Compile => None
			case Test => Some(Artifact.TestsClassifier)
			case c => Some(c.name)
		}
		val combined = cPart.toList ++ classifier.toList
		if(combined.isEmpty) a.copy(classifier = None, configurations = cOpt.toList) else {
			val classifierString = combined mkString "-"
			val confs = cOpt.toList flatMap { c => artifactConfigurations(a, c, classifier) }
			a.copy(classifier = Some(classifierString), `type` = Artifact.classifierType(classifierString), configurations = confs)
		}
	}
	def artifactConfigurations(base: Artifact, scope: Configuration, classifier: Option[String]): Iterable[Configuration] =
		classifier match {
			case Some(c) => Artifact.classifierConf(c) :: Nil
			case None => scope :: Nil
		}

	@deprecated("Use `Util.pairID` instead", "0.12.0")
	def pairID = Util.pairID

	@deprecated("Use the cacheDirectory val on streams.", "0.13.0")
	def perTaskCache(key: TaskKey[_]): Setting[File] =
		cacheDirectory ~= { _ / ("for_" + key.key.label) }

	@deprecated("Use `packageTaskSettings` instead", "0.12.0")
	def packageTasks(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) = packageTaskSettings(key, mappingsTask)
	def packageTaskSettings(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in TaskGlobal <<= packageTask,
			packageConfiguration <<= packageConfigurationTask,
			mappings <<= mappingsTask,
			packagedArtifact := (artifact.value, key.value),
			artifact <<= artifactSetting,
			artifactPath <<= artifactPathSetting(artifact)
		))
	def packageTask: Initialize[Task[File]] =
		(packageConfiguration, streams) map { (config, s) =>
			Package(config, s.cacheDirectory, s.log)
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
			val (dirs, files) = preserve.filter(_.exists).flatMap(_.***.get).partition(_.isDirectory)
			val mappings = files.zipWithIndex map { case (f, i) => (f, new File(temp, i.toHexString)) }
			IO.move(mappings)
			IO.delete(clean)
			IO.createDirectories(dirs) // recreate empty directories
			IO.move(mappings.map(_.swap))
		}
	def runMainTask(classpath: Initialize[Task[Classpath]], scalaRun: Initialize[Task[ScalaRun]]): Initialize[InputTask[Unit]] =
	{
		import DefaultParsers._
		val parser = loadForParser(discoveredMainClasses)( (s, names) => runMainParser(s, names getOrElse Nil) )
		Def.inputTask {
			val res = parser.parsed
			val mainClass = res._1
			val args = res._2
			toError(scalaRun.value.run(mainClass, data(classpath.value), args, streams.value.log))
		}
	}

	def runTask(classpath: Initialize[Task[Classpath]], mainClassTask: Initialize[Task[Option[String]]], scalaRun: Initialize[Task[ScalaRun]]): Initialize[InputTask[Unit]] =
	{
		import Def.parserToInput
		val parser = Def.spaceDelimited()
		Def.inputTask {
			val mainClass = mainClassTask.value getOrElse error("No main class detected.")
			toError(scalaRun.value.run(mainClass, data(classpath.value), parser.parsed, streams.value.log))
		}
	}

	def runnerTask = runner <<= runnerInit
	def runnerInit: Initialize[Task[ScalaRun]] = Def.task {
		val tmp = taskTemporaryDirectory.value
		val si = scalaInstance.value
		if(fork.value) new ForkRun(forkOptions.value) else new Run(si, trapExit.value, tmp)
	}

	@deprecated("Use `docTaskSettings` instead", "0.12.0")
	def docSetting(key: TaskKey[File]) = docTaskSettings(key)
	def docTaskSettings(key: TaskKey[File] = doc): Seq[Setting[_]] = inTask(key)(compileInputsSettings ++ Seq(
		apiMappings ++= { if(autoAPIMappings.value) APIMappings.extract(dependencyClasspath.value, streams.value.log).toMap else Map.empty[File,URL] },
		key in TaskGlobal <<= (compileInputs, target, configuration, apiMappings, streams) map { (in, out, config, xapis, s) =>
			val srcs = in.config.sources
			val hasScala = srcs.exists(_.name.endsWith(".scala"))
			val hasJava = srcs.exists(_.name.endsWith(".java"))
			val cp = in.config.classpath.toList.filterNot(_ == in.config.classesDirectory)
			val label = nameForSrc(config.name)
			val (options, runDoc) =
				if(hasScala)
					(in.config.options ++ Opts.doc.externalAPI(xapis),
						Doc.scaladoc(label, s.cacheDirectory / "scala", in.compilers.scalac.onArgs(exported(s, "scaladoc"))))
				else if(hasJava)
					(in.config.javacOptions,
						Doc.javadoc(label, s.cacheDirectory / "java", in.compilers.javac.onArgs(exported(s, "javadoc"))))
				else
					(Nil, RawCompileLike.nop)
			runDoc(srcs, cp, out, options, in.config.maxErrors, s.log)
			out
		}
	))

	def mainRunTask = run <<= runTask(fullClasspath in Runtime, mainClass in run, runner in run)
	def mainRunMainTask = runMain <<= runMainTask(fullClasspath in Runtime, runner in run)

	def discoverMainClasses(analysis: inc.Analysis): Seq[String] =
		Discovery.applications(Tests.allDefs(analysis)) collect { case (definition, discovered) if(discovered.hasMain) => definition.name }

	def consoleProjectTask = (state, streams, initialCommands in consoleProject) map { (state, s, extra) => ConsoleProject(state, extra)(s.log); println() }
	def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
	def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
	def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] =
		(compilers in task, classpath in task, scalacOptions in task, initialCommands in task, cleanupCommands in task, taskTemporaryDirectory in task, scalaInstance in task, streams) map {
			(cs, cp, options, initCommands, cleanup, temp, si, s) =>
				val cpFiles = data(cp)
				val fullcp = (cpFiles ++ si.jars).distinct
				val loader = sbt.classpath.ClasspathUtilities.makeLoader(fullcp, si, IO.createUniqueDirectory(temp))
				val compiler = cs.scalac.onArgs(exported(s, "scala"))
				(new Console(compiler))(cpFiles, options, loader, initCommands, cleanup)()(s.log).foreach(msg => error(msg))
				println()
		}

	private[this] def exported(w: PrintWriter, command: String): Seq[String] => Unit = args =>
		w.println( (command +: args).mkString(" ") )
	private[this] def exported(s: TaskStreams, command: String): Seq[String] => Unit = args =>
		exported(s.text("export"), command)

	def compileTaskSettings: Seq[Setting[_]] = inTask(compile)(compileInputsSettings)

	def compileTask: Initialize[Task[inc.Analysis]] = Def.task { compileTaskImpl(streams.value, (compileInputs in compile).value) }
	private[this] def compileTaskImpl(s: TaskStreams, ci: Compiler.Inputs): inc.Analysis =
	{
		lazy val x = s.text("export")
		def onArgs(cs: Compiler.Compilers) = cs.copy(scalac = cs.scalac.onArgs(exported(x, "scalac")), javac = cs.javac.onArgs(exported(x, "javac")))
		val i = ci.copy(compilers = onArgs(ci.compilers))
		Compiler(i,s.log)
	}
	def compileIncSetupTask =
		(dependencyClasspath, skip in compile, definesClass, compilerCache, streams, incOptions) map { (cp, skip, definesC, cache, s, incOptions) =>
			Compiler.IncSetup(analysisMap(cp), definesC, skip, s.cacheDirectory / "inc_compile", cache, incOptions)
		}
	def compileInputsSettings: Seq[Setting[_]] =
		Seq(compileInputs := {
			val cp = classDirectory.value +: data(dependencyClasspath.value)
			Compiler.inputs(cp, sources.value, classDirectory.value, scalacOptions.value, javacOptions.value, maxErrors.value, sourcePositionMappers.value, compileOrder.value)(compilers.value, compileIncSetup.value, streams.value.log)
		})

	def printWarningsTask: Initialize[Task[Unit]] =
		(streams, compile, maxErrors, sourcePositionMappers) map { (s, analysis, max, spms) =>
			val problems = analysis.infos.allInfos.values.flatMap(i =>  i.reportedProblems++ i.unreportedProblems)
			val reporter = new LoggerReporter(max, s.log, Compiler.foldMappers(spms))
			problems foreach { p => reporter.display(p.position, p.message, p.severity) }
		}

	def sbtPluginExtra(m: ModuleID, sbtV: String, scalaV: String): ModuleID =
		m.extra(CustomPomParser.SbtVersionKey -> sbtV, CustomPomParser.ScalaVersionKey -> scalaV).copy(crossVersion = CrossVersion.Disabled)
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
	(classDirectory, resources, resourceDirectories, streams) map { (target, resrcs, dirs, s) =>
		val cacheFile = s.cacheDirectory / "copy-resources"
		val mappings = (resrcs --- dirs) pair (rebase(dirs, target) | flat(target))
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

	@deprecated("Use the new <key>.all(<ScopeFilter>) API", "0.13.0")
	def inDependencies[T](key: SettingKey[T], default: ProjectRef => T, includeRoot: Boolean = true, classpath: Boolean = true, aggregate: Boolean = false): Initialize[Seq[T]] =
		forDependencies[T,T](ref => (key in ref) ?? default(ref), includeRoot, classpath, aggregate)

	@deprecated("Use the new <key>.all(<ScopeFilter>) API", "0.13.0")
	def forDependencies[T,V](init: ProjectRef => Initialize[V], includeRoot: Boolean = true, classpath: Boolean = true, aggregate: Boolean = false): Initialize[Seq[V]] =
		Def.bind( (loadedBuild, thisProjectRef).identity ) { case (lb, base) =>
			transitiveDependencies(base, lb, includeRoot, classpath, aggregate) map init join ;
		}

	def transitiveDependencies(base: ProjectRef, structure: LoadedBuild, includeRoot: Boolean, classpath: Boolean = true, aggregate: Boolean = false): Seq[ProjectRef] =
	{
		def tdeps(enabled: Boolean, f: ProjectRef => Seq[ProjectRef]): Seq[ProjectRef] =
		{
			val full = if(enabled) Dag.topologicalSort(base)(f) else Nil
			if(includeRoot) full else full dropRight 1
		}
		def fullCp = tdeps(classpath, getDependencies(structure, classpath=true, aggregate=false))
		def fullAgg = tdeps(aggregate, getDependencies(structure, classpath=false, aggregate=true))
		(classpath, aggregate) match {
			case (true, true) => (fullCp ++ fullAgg).distinct
			case (true, false) => fullCp
			case _ => fullAgg
		}
	}
	def getDependencies(structure: LoadedBuild, classpath: Boolean = true, aggregate: Boolean = false): ProjectRef => Seq[ProjectRef] =
		ref => Project.getProject(ref, structure).toList flatMap { p =>
			(if(classpath) p.dependencies.map(_.project) else Nil) ++
			(if(aggregate) p.aggregate else Nil)
		}

	val CompletionsID = "completions"

	def noAggregation: Seq[Scoped] = Seq(run, runMain, console, consoleQuick, consoleProject)
	lazy val disableAggregation = noAggregation map disableAggregate
	def disableAggregate(k: Scoped) =
		aggregate in Scope.GlobalScope.copy(task = Select(k.key)) :== false

	lazy val baseTasks: Seq[Setting[_]] = projectTasks ++ packageBase

	lazy val baseClasspaths: Seq[Setting[_]] = Classpaths.publishSettings ++ Classpaths.baseSettings
	lazy val configSettings: Seq[Setting[_]] = Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig ++ Classpaths.compilerPluginConfig

	lazy val compileSettings: Seq[Setting[_]] = configSettings ++ (mainRunMainTask +: mainRunTask +: addBaseSources) ++ Classpaths.addUnmanagedLibrary
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

	lazy val configSettings: Seq[Setting[_]] = classpaths ++ Seq(
		products <<= makeProducts,
		productDirectories := compileInputs.value.config.classesDirectory :: Nil,
		classpathConfiguration := findClasspathConfig(internalConfigurationMap.value, configuration.value, classpathConfiguration.?.value, update.value)
	)
	private[this] def classpaths: Seq[Setting[_]] = Seq(
		externalDependencyClasspath <<= concat(unmanagedClasspath, managedClasspath),
		dependencyClasspath <<= concat(internalDependencyClasspath, externalDependencyClasspath),
		fullClasspath <<= concatDistinct(exportedProducts, dependencyClasspath),
		internalDependencyClasspath <<= internalDependencies,
		unmanagedClasspath <<= unmanagedDependencies,
		managedClasspath := managedJars(classpathConfiguration.value, classpathTypes.value, update.value),
		exportedProducts <<= exportProductsTask,
		unmanagedJars := findUnmanagedJars(configuration.value, unmanagedBase.value, includeFilter in unmanagedJars value, excludeFilter in unmanagedJars value)
	).map(exportClasspath)

	private[this] def exportClasspath(s: Setting[Task[Classpath]]): Setting[Task[Classpath]] =
		s.mapInitialize(init => Def.task { exportClasspath(streams.value, init.value) })
	private[this] def exportClasspath(s: TaskStreams, cp: Classpath): Classpath =
	{
		s.text("export").println(Path.makeString(data(cp)))
		cp
	}

	def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
	lazy val defaultPackages: Seq[TaskKey[File]] =
		for(task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
	lazy val defaultArtifactTasks: Seq[TaskKey[File]] = makePom +: defaultPackages

	def findClasspathConfig(map: Configuration => Configuration, thisConfig: Configuration, delegated: Option[Configuration], report: UpdateReport): Configuration =
	{
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
		publishArtifact in GlobalScope :== true,
		publishArtifact in GlobalScope in Test:== false,
		artifacts <<= artifactDefs(defaultArtifactTasks),
		packagedArtifacts <<= packaged(defaultArtifactTasks),
		makePom := { val config = makePomConfiguration.value; IvyActions.makePom(ivyModule.value, config, streams.value.log); config.file },
		packagedArtifact in makePom := (artifact in makePom value, makePom value),
		deliver <<= deliverTask(deliverConfiguration),
		deliverLocal <<= deliverTask(deliverLocalConfiguration),
		publish <<= publishTask(publishConfiguration, deliver),
		publishLocal <<= publishTask(publishLocalConfiguration, deliverLocal),
		publishM2 <<= publishTask(publishM2Configuration, deliverLocal)
	)
	val baseSettings: Seq[Setting[_]] = sbtClassifiersTasks ++ Seq(
		conflictWarning in GlobalScope :== ConflictWarning.default("global"),
		conflictWarning := conflictWarning.value.copy(label = Reference.display(thisProjectRef.value)),
		unmanagedBase := baseDirectory.value / "lib",
		normalizedName := StringUtilities.normalize(name.value),
		isSnapshot <<= isSnapshot or version(_ endsWith "-SNAPSHOT"),
		description <<= description or name,
		homepage in GlobalScope :== None,
		startYear in GlobalScope :== None,
		licenses in GlobalScope :== Nil,
		organization <<= organization or normalizedName,
		organizationName <<= organizationName or organization,
		organizationHomepage <<= organizationHomepage or homepage,
		scmInfo in GlobalScope :== None,
		projectInfo <<= (name, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo) apply ModuleInfo,
		overrideBuildResolvers <<= appConfiguration(isOverrideRepositories),
		externalResolvers <<= (externalResolvers.task.?, resolvers, appResolvers) {
			case (Some(delegated), Seq(), _) => delegated
			case (_, rs, Some(ars)) => task { ars ++ rs }  // TODO - Do we need to filter out duplicates?
			case (_, rs, _) => task { Resolver.withDefaultResolvers(rs) }
		},
		appResolvers <<= appConfiguration apply appRepositories,
		bootResolvers <<= appConfiguration map bootRepositories,
		fullResolvers <<= (projectResolver,externalResolvers,sbtPlugin,sbtResolver,bootResolvers,overrideBuildResolvers) map { (proj,rs,isPlugin,sbtr, boot, overrideFlag) =>
			boot match {
				case Some(repos) if overrideFlag => proj +: repos
				case _ =>
					val base = if(isPlugin) sbtr +: sbtPluginReleases +: rs else rs
					proj +: base
			}
		},
		offline in GlobalScope :== false,
		moduleName <<= normalizedName,
		defaultConfiguration in GlobalScope :== Some(Configurations.Compile),
		defaultConfigurationMapping in GlobalScope <<= defaultConfiguration{ case Some(d) => "*->" + d.name; case None => "*->*" },
		ivyPaths := new IvyPaths(baseDirectory.value, bootIvyHome(appConfiguration.value)),
		otherResolvers := Resolver.publishMavenLocal :: publishTo.value.toList,
		projectResolver <<= projectResolverTask,
		projectDependencies <<= projectDependenciesTask,
		dependencyOverrides in GlobalScope :== Set.empty,
		libraryDependencies in GlobalScope :== Nil,
		libraryDependencies ++= autoLibraryDependency(autoScalaLibrary.value && !scalaHome.value.isDefined && managedScalaInstance.value, sbtPlugin.value, scalaOrganization.value, scalaVersion.value),
		allDependencies := {
			val base = projectDependencies.value ++ libraryDependencies.value
			val pluginAdjust = if(sbtPlugin.value) sbtDependency.value.copy(configurations = Some(Provided.name)) +: base else base
			if(scalaHome.value.isDefined || ivyScala.value.isEmpty || !managedScalaInstance.value)
				pluginAdjust
			else
				ScalaArtifacts.toolDependencies(scalaOrganization.value, scalaVersion.value) ++ pluginAdjust
		},
		ivyLoggingLevel in GlobalScope :== UpdateLogging.DownloadOnly,
		ivyXML in GlobalScope :== NodeSeq.Empty,
		ivyValidate in GlobalScope :== false,
		ivyScala <<= ivyScala or (scalaHome, scalaVersion in update, scalaBinaryVersion in update, scalaOrganization) { (sh,fv,bv,so) =>
			Some(new IvyScala(fv, bv, Nil, filterImplicit = false, checkExplicit = true, overrideScalaVersion = false, scalaOrganization = so))
		},
		moduleConfigurations in GlobalScope :== Nil,
		publishTo in GlobalScope :== None,
		artifactPath in makePom <<= artifactPathSetting(artifact in makePom),
		publishArtifact in makePom := publishMavenStyle.value && publishArtifact.value,
		artifact in makePom := Artifact.pom(moduleName.value),
		projectID <<= defaultProjectID,
		projectID <<= pluginProjectID,
		resolvers in GlobalScope :== Nil,
		projectDescriptors <<= depMap,
		retrievePattern in GlobalScope :== Resolver.defaultRetrievePattern,
		updateConfiguration := new UpdateConfiguration(retrieveConfiguration.value, false, ivyLoggingLevel.value),
		retrieveConfiguration := { if(retrieveManaged.value) Some(new RetrieveConfiguration(managedDirectory.value, retrievePattern.value)) else None },
		ivyConfiguration <<= mkIvyConfiguration,
		ivyConfigurations := {
			val confs = thisProject.value.configurations
			(confs ++ confs.map(internalConfigurationMap.value) ++ (if(autoCompilerPlugins.value) CompilerPlugin :: Nil else Nil)).distinct
		},
		ivyConfigurations ++= Configurations.auxiliary,
		ivyConfigurations ++= { if(managedScalaInstance.value && !scalaHome.value.isDefined) Configurations.ScalaTool :: Nil else Nil },
		moduleSettings <<= moduleSettings0,
		makePomConfiguration := new MakePomConfiguration(artifactPath in makePom value, projectInfo.value, None, pomExtra.value, pomPostProcess.value, pomIncludeRepository.value, pomAllRepositories.value),
		deliverLocalConfiguration := deliverConfig(crossTarget.value, status = if (isSnapshot.value) "integration" else "release", logging = ivyLoggingLevel.value ),
		deliverConfiguration <<= deliverLocalConfiguration,
		publishConfiguration := publishConfig(packagedArtifacts.value, if(publishMavenStyle.value) None else Some(deliver.value), resolverName = getPublishTo(publishTo.value).name, checksums = checksums.in(publish).value, logging = ivyLoggingLevel.value),
		publishLocalConfiguration := publishConfig(packagedArtifacts.value, Some(deliverLocal.value), checksums.in(publishLocal).value, logging = ivyLoggingLevel.value ),
		publishM2Configuration := publishConfig(packagedArtifacts.value, None, resolverName = Resolver.publishMavenLocal.name, checksums = checksums.in(publishM2).value, logging = ivyLoggingLevel.value),
		ivySbt <<= ivySbt0,
		ivyModule := { val is = ivySbt.value; new is.Module(moduleSettings.value) },
		transitiveUpdate <<= transitiveUpdateTask,
		update <<= updateTask tag(Tags.Update, Tags.Network),
		update := { val report = update.value; ConflictWarning(conflictWarning.value, report, streams.value.log); report },
		transitiveClassifiers in GlobalScope :== Seq(SourceClassifier, DocClassifier),
		classifiersModule in updateClassifiers := GetClassifiersModule(projectID.value, update.value.allModules, ivyConfigurations.in(updateClassifiers).value, transitiveClassifiers.in(updateClassifiers).value),
		updateClassifiers <<= (ivySbt, classifiersModule in updateClassifiers, updateConfiguration, ivyScala, appConfiguration, streams) map { (is, mod, c, ivyScala, app, s) =>
			val out = is.withIvy(s.log)(_.getSettings.getDefaultIvyUserDir)
			withExcludes(out, mod.classifiers, lock(app)) { excludes =>
				IvyActions.updateClassifiers(is, GetClassifiersConfiguration(mod, excludes, c, ivyScala), s.log)
			}
		} tag(Tags.Update, Tags.Network),
		sbtDependency in GlobalScope := {
			val app = appConfiguration.value
			val id = app.provider.id
			val scalaVersion = app.provider.scalaProvider.version
			val binVersion = binaryScalaVersion(scalaVersion)
			val cross = if(id.crossVersioned) CrossVersion.binary else CrossVersion.Disabled
			val base = ModuleID(id.groupID, id.name, id.version, crossVersion = cross)
			CrossVersion(scalaVersion, binVersion)(base).copy(crossVersion = CrossVersion.Disabled)
		}
	)
	def warnResolversConflict(ress: Seq[Resolver], log: Logger) {
		val resset = ress.toSet
		for ((name, r) <- resset groupBy (_.name) if r.size > 1) {
			log.warn("Multiple resolvers having different access mechanism configured with same name '" + name + "'. To avoid conflict, Remove duplicate project resolvers (`resolvers`) or rename publishing resolver (`publishTo`).")
		}
	}

	private[sbt] def defaultProjectID: Initialize[ModuleID] = Def.setting {
		val base = ModuleID(organization.value, moduleName.value, version.value).cross(crossVersion in projectID value).artifacts(artifacts.value : _*)
		apiURL.value match {
			case Some(u) if autoAPIMappings.value => base.extra(CustomPomParser.ApiURLKey -> u.toExternalForm)
			case _ => base
		}
	}

	def pluginProjectID: Initialize[ModuleID] = (sbtBinaryVersion in update, scalaBinaryVersion in update, projectID, sbtPlugin) {
		(sbtBV, scalaBV, pid, isPlugin) =>
			if(isPlugin) sbtPluginExtra(pid, sbtBV, scalaBV) else pid
	}
	def ivySbt0: Initialize[Task[IvySbt]] =
		(ivyConfiguration, credentials, streams) map { (conf, creds, s) =>
			Credentials.register(creds, s.log)
			new IvySbt(conf)
		}
	def moduleSettings0: Initialize[Task[ModuleSettings]] = Def.task {
		new InlineConfiguration(projectID.value, projectInfo.value, allDependencies.value, dependencyOverrides.value, ivyXML.value, ivyConfigurations.value, defaultConfiguration.value, ivyScala.value, ivyValidate.value, conflictManager.value)
	}

	def sbtClassifiersTasks = inTask(updateSbtClassifiers)(Seq(
		transitiveClassifiers in GlobalScope in updateSbtClassifiers ~= ( _.filter(_ != DocClassifier) ),
		externalResolvers := {
			val explicit = buildStructure.value.units(thisProjectRef.value.build).unit.plugins.pluginData.resolvers
			explicit orElse bootRepositories(appConfiguration.value) getOrElse externalResolvers.value
		},
		ivyConfiguration := new InlineIvyConfiguration(ivyPaths.value, externalResolvers.value, Nil, Nil, offline.value, Option(lock(appConfiguration.value)), checksums.value, Some(target.value / "resolution-cache"), streams.value.log),
		ivySbt <<= ivySbt0,
		classifiersModule <<= (projectID, sbtDependency, transitiveClassifiers, loadedBuild, thisProjectRef) map { ( pid, sbtDep, classifiers, lb, ref) =>
			val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
			GetClassifiersModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil, classifiers)
		},
		updateSbtClassifiers in TaskGlobal <<= (ivySbt, classifiersModule, updateConfiguration, ivyScala, appConfiguration, streams) map {
				(is, mod, c, ivyScala, app, s) =>
			val out = is.withIvy(s.log)(_.getSettings.getDefaultIvyUserDir)
			withExcludes(out, mod.classifiers, lock(app)) { excludes =>
				val noExplicitCheck = ivyScala.map(_.copy(checkExplicit=false))
				IvyActions.transitiveScratch(is, "sbt", GetClassifiersConfiguration(mod, excludes, c, noExplicitCheck), s.log)
			}
		} tag(Tags.Update, Tags.Network)
	))

	def deliverTask(config: TaskKey[DeliverConfiguration]): Initialize[Task[File]] =
		(ivyModule, config, update, streams) map { (module, config, _, s) => IvyActions.deliver(module, config, s.log) }
	def publishTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]): Initialize[Task[Unit]] =
		(ivyModule, config, streams) map { (module, config, s) =>
			IvyActions.publish(module, config, s.log)
		} tag(Tags.Publish, Tags.Network)

		import Cache._
		import CacheIvy.{classpathFormat, /*publishIC,*/ updateIC, updateReportFormat, excludeMap}

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

	def updateTask: Initialize[Task[UpdateReport]] = Def.task {
		val depsUpdated = transitiveUpdate.value.exists(!_.stats.cached)
		val isRoot = executionRoots.value contains resolvedScoped.value
		val s = streams.value
		val scalaProvider = appConfiguration.value.provider.scalaProvider

		// Only substitute unmanaged jars for managed jars when the major.minor parts of the versions the same for:
		//   the resolved Scala version and the scalaHome version: compatible (weakly- no qualifier checked)
		//   the resolved Scala version and the declared scalaVersion: assume the user intended scalaHome to override anything with scalaVersion
		def subUnmanaged(subVersion: String, jars: Seq[File])  =  (sv: String) =>
			(partialVersion(sv), partialVersion(subVersion), partialVersion(scalaVersion.value)) match {
				case (Some(res), Some(sh), _) if res == sh => jars
				case (Some(res), _, Some(decl)) if res == decl => jars
				case _ => Nil
			}
		val subScalaJars: String => Seq[File] = Defaults.unmanagedScalaInstanceOnly.value match {
			case Some(si) => subUnmanaged(si.version, si.jars)
			case None => sv => if(scalaProvider.version == sv) scalaProvider.jars else Nil
		}
		val transform: UpdateReport => UpdateReport = r => substituteScalaFiles(scalaOrganization.value, r)(subScalaJars)

		val show = Reference.display(thisProjectRef.value)
		cachedUpdate(s.cacheDirectory, show, ivyModule.value, updateConfiguration.value, transform, skip = (skip in update).value, force = isRoot, depsUpdated = depsUpdated, log = s.log)
	}

	def cachedUpdate(cacheFile: File, label: String, module: IvySbt#Module, config: UpdateConfiguration, transform: UpdateReport=>UpdateReport, skip: Boolean, force: Boolean, depsUpdated: Boolean, log: Logger): UpdateReport =
	{
		implicit val updateCache = updateIC
		type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil
		def work = (_:  In) match { case conf :+: settings :+: config :+: HNil =>
			log.info("Updating " + label + "...")
			val r = IvyActions.update(module, config, log)
			log.info("Done updating.")
			transform(r)
		}
		def uptodate(inChanged: Boolean, out: UpdateReport): Boolean =
			!force &&
			!depsUpdated &&
			!inChanged &&
			out.allFiles.forall(f => fileUptodate(f,out.stamps)) &&
			fileUptodate(out.cachedDescriptor, out.stamps)

		val outCacheFile = cacheFile / "output"
		def skipWork: In => UpdateReport =
			Tracked.lastOutput[In, UpdateReport](outCacheFile) {
				case (_, Some(out)) => out
				case _ => error("Skipping update requested, but update has not previously run successfully.")
			}
		def doWork: In => UpdateReport =
			Tracked.inputChanged(cacheFile / "inputs") { (inChanged: Boolean, in: In) =>
				val outCache = Tracked.lastOutput[In, UpdateReport](outCacheFile) {
					case (_, Some(out)) if uptodate(inChanged, out) => out
					case _ => work(in)
				}
				outCache(in)
			}
		val f = if(skip && !force) skipWork else doWork
		f(module.owner.configuration :+: module.moduleSettings :+: config :+: HNil)
	}
	private[this] def fileUptodate(file: File, stamps: Map[File, Long]): Boolean =
		stamps.get(file).forall(_ == file.lastModified)
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
		(thisProjectRef, settingsData, buildDependencies) map { (ref, data, deps) =>
			deps.classpath(ref) flatMap { dep => (projectID in dep.project) get data map {
				_.copy(configurations = dep.configuration, explicitArtifacts = Nil) }
			}
	}

	def depMap: Initialize[Task[Map[ModuleRevisionId, ModuleDescriptor]]] =
		(thisProjectRef, settingsData, buildDependencies, streams) flatMap { (root, data, deps, s) =>
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
		(products.task, packageBin.task, exportJars, compile, apiURL) flatMap { (psTask, pkgTask, useJars, analysis, u) =>
			(if(useJars) Seq(pkgTask).join else psTask) map { _ map { f => APIMappings.store(analyzed(f, analysis), u) } }
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
		(thisProjectRef, classpathConfiguration, configuration, settingsData, buildDependencies) flatMap internalDependencies0
	def unmanagedDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, configuration, settingsData, buildDependencies) flatMap unmanagedDependencies0
	def mkIvyConfiguration: Initialize[Task[IvyConfiguration]] =
		(fullResolvers, ivyPaths, otherResolvers, moduleConfigurations, offline, checksums in update, appConfiguration, target, streams) map { (rs, paths, other, moduleConfs, off, check, app, t, s) =>
			warnResolversConflict(rs ++: other, s.log)
			val resCacheDir = t / "resolution-cache"
			new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, Some(lock(app)), check, Some(resCacheDir), s.log)
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

	lazy val typesafeReleases = Resolver.typesafeIvyRepo("releases")
	lazy val typesafeSnapshots = Resolver.typesafeIvyRepo("snapshots")
	@deprecated("Use `typesafeReleases` instead", "0.12.0")
	lazy val typesafeResolver = typesafeReleases
	@deprecated("Use `Resolver.typesafeIvyRepo` instead", "0.12.0")
	def typesafeRepo(status: String) = Resolver.typesafeIvyRepo(status)

	lazy val sbtPluginReleases = Resolver.sbtPluginRepo("releases")
	lazy val sbtPluginSnapshots = Resolver.sbtPluginRepo("snapshots")

	def modifyForPlugin(plugin: Boolean, dep: ModuleID): ModuleID =
		if(plugin) dep.copy(configurations = Some(Provided.name)) else dep

	@deprecated("Explicitly specify the organization using the other variant.", "0.13.0")
	def autoLibraryDependency(auto: Boolean, plugin: Boolean, version: String): Seq[ModuleID] =
		if(auto)
			modifyForPlugin(plugin, ScalaArtifacts.libraryDependency(version)) :: Nil
		else
			Nil
	def autoLibraryDependency(auto: Boolean, plugin: Boolean, org: String, version: String): Seq[ModuleID] =
		if(auto)
			modifyForPlugin(plugin, ModuleID(org, ScalaArtifacts.LibraryID, version)) :: Nil
		else
			Nil
	def addUnmanagedLibrary: Seq[Setting[_]] = Seq(
		unmanagedJars in Compile <++= unmanagedScalaLibrary
	)
	def unmanagedScalaLibrary: Initialize[Task[Seq[File]]] =
		Def.taskDyn {
			if(autoScalaLibrary.value && scalaHome.value.isDefined)
				Def.task { scalaInstance.value.libraryJar :: Nil }
			else
				Def.task { Nil }
		}

		import DependencyFilter._
	def managedJars(config: Configuration, jarTypes: Set[String], up: UpdateReport): Classpath =
		up.filter( configurationFilter(config.name) && artifactFilter(`type` = jarTypes) ).toSeq.map { case (conf, module, art, file) =>
			Attributed(file)(AttributeMap.empty.put(artifact.key, art).put(moduleID.key, module).put(configuration.key, config))
		} distinct;

	def findUnmanagedJars(config: Configuration, base: File, filter: FileFilter, excl: FileFilter): Classpath =
		(base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath


	@deprecated("Specify the classpath that includes internal dependencies", "0.13.0")
	def autoPlugins(report: UpdateReport): Seq[String] = autoPlugins(report, Nil)
	def autoPlugins(report: UpdateReport, internalPluginClasspath: Seq[File]): Seq[String] =
	{
		val pluginClasspath = report.matching(configurationFilter(CompilerPlugin.name)) ++ internalPluginClasspath
		val plugins = classpath.ClasspathUtilities.compilerPlugins(pluginClasspath)
		plugins.map("-Xplugin:" + _.getAbsolutePath).toSeq
	}

	private[this] lazy val internalCompilerPluginClasspath: Initialize[Task[Classpath]] = 
		(thisProjectRef, settingsData, buildDependencies) flatMap { (ref, data, deps) =>
			internalDependencies0(ref, CompilerPlugin, CompilerPlugin, data, deps)
		}

	lazy val compilerPluginConfig = Seq(
		scalacOptions := {
			val options = scalacOptions.value
			if(autoCompilerPlugins.value) options ++ autoPlugins(update.value, internalCompilerPluginClasspath.value.files) else options
		}
	)
	@deprecated("Doesn't properly handle non-standard Scala organizations.", "0.13.0")
	def substituteScalaFiles(scalaInstance: ScalaInstance, report: UpdateReport): UpdateReport =
		substituteScalaFiles(scalaInstance, ScalaArtifacts.Organization, report)

	@deprecated("Directly provide the jar files per Scala version.", "0.13.0")
	def substituteScalaFiles(scalaInstance: ScalaInstance, scalaOrg: String, report: UpdateReport): UpdateReport =
		substituteScalaFiles(scalaOrg, report)(const(scalaInstance.jars))
		
	def substituteScalaFiles(scalaOrg: String, report: UpdateReport)(scalaJars: String => Seq[File]): UpdateReport =
		report.substitute { (configuration, module, arts) =>
			import ScalaArtifacts._
			if(module.organization == scalaOrg) {
				val jarName = module.name + ".jar"
				val replaceWith = scalaJars(module.revision).filter(_.getName == jarName).map(f => (Artifact(f.getName.stripSuffix(".jar")), f))
				if(replaceWith.isEmpty) arts else replaceWith
			} else
				arts
		}

		// try/catch for supporting earlier launchers
	def bootIvyHome(app: xsbti.AppConfiguration): Option[File] =
		try { Option(app.provider.scalaProvider.launcher.ivyHome) }
		catch { case _: NoSuchMethodError => None }

	def bootChecksums(app: xsbti.AppConfiguration): Seq[String] =
		try { app.provider.scalaProvider.launcher.checksums.toSeq }
		catch { case _: NoSuchMethodError => IvySbt.DefaultChecksums }

	def isOverrideRepositories(app: xsbti.AppConfiguration): Boolean =
		try app.provider.scalaProvider.launcher.isOverrideRepositories
		catch { case _: NoSuchMethodError => false }

	/** Loads the `appRepositories` configured for this launcher, if supported. */
	def appRepositories(app: xsbti.AppConfiguration): Option[Seq[Resolver]] =
		try { Some(app.provider.scalaProvider.launcher.appRepositories.toSeq map bootRepository) }
		catch { case _: NoSuchMethodError => None }

	def bootRepositories(app: xsbti.AppConfiguration): Option[Seq[Resolver]] =
		try { Some(app.provider.scalaProvider.launcher.ivyRepositories.toSeq map bootRepository) }
		catch { case _: NoSuchMethodError => None }

	private[this] def mavenCompatible(ivyRepo: xsbti.IvyRepository): Boolean =
		try { ivyRepo.mavenCompatible }
		catch { case _: NoSuchMethodError => false }

	private[this] def bootRepository(repo: xsbti.Repository): Resolver =
	{
		import xsbti.Predefined
		repo match
		{
			case m: xsbti.MavenRepository => MavenRepository(m.id, m.url.toString)
			case i: xsbti.IvyRepository => Resolver.url(i.id, i.url)(Patterns(i.ivyPattern :: Nil, i.artifactPattern :: Nil, mavenCompatible(i)))
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

	/** Defines an alias given by `name` that expands to `value`.
	* This alias is defined globally after projects are loaded.
	* The alias is undefined when projects are unloaded.
	* Names are restricted to be either alphanumeric or completely symbolic.
	* As an exception, '-' and '_' are allowed within an alphanumeric name.*/
	def addCommandAlias(name: String, value: String): Seq[Setting[State => State]] =
	{
		val add = (s: State) => BasicCommands.addAlias(s, name, value)
		val remove = (s: State) => BasicCommands.removeAlias(s, name)
		def compose(setting: SettingKey[State => State], f: State => State) = setting in Global ~= (_ compose f)
		Seq( compose(onLoad, add), compose(onUnload, remove) )
	}
	def addSbtPlugin(dependency: ModuleID, sbtVersion: String, scalaVersion: String): Setting[Seq[ModuleID]] =
		libraryDependencies += sbtPluginExtra(dependency, sbtVersion, scalaVersion)
	def addSbtPlugin(dependency: ModuleID, sbtVersion: String): Setting[Seq[ModuleID]] =
		libraryDependencies <+= (scalaBinaryVersion in update) { scalaV => sbtPluginExtra(dependency, sbtVersion, scalaV) }
	def addSbtPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
		libraryDependencies <+= (sbtBinaryVersion in update,scalaBinaryVersion in update) { (sbtV, scalaV) => sbtPluginExtra(dependency, sbtV, scalaV) }

	def compilerPlugin(dependency: ModuleID): ModuleID =
		dependency.copy(configurations = Some("plugin->default(compile)"))

	def addCompilerPlugin(dependency: ModuleID): Setting[Seq[ModuleID]] =
		libraryDependencies += compilerPlugin(dependency)

	def addArtifact(a: Artifact, taskDef: TaskKey[File]): SettingsDefinition =
	{
		val pkgd = packagedArtifacts := packagedArtifacts.value updated (a, taskDef.value)
		seq( artifacts += a, pkgd )
	}
	def addArtifact(artifact: Initialize[Artifact], taskDef: Initialize[Task[File]]): SettingsDefinition =
	{
		val artLocal = SettingKey.local[Artifact]
		val taskLocal = TaskKey.local[File]
		val art = artifacts := artLocal.value +: artifacts.value
		val pkgd = packagedArtifacts := packagedArtifacts.value updated (artLocal.value, taskLocal.value)
		seq( artLocal := artifact.value, taskLocal := taskDef.value, art, pkgd )
	}

	def seq(settings: Setting[_]*): SettingsDefinition = new Def.SettingList(settings)

	def externalIvySettings(file: Initialize[File] = inBase("ivysettings.xml"), addMultiResolver: Boolean = true): Setting[Task[IvyConfiguration]] =
		externalIvySettingsURI(file(_.toURI), addMultiResolver)
	def externalIvySettingsURL(url: URL, addMultiResolver: Boolean = true): Setting[Task[IvyConfiguration]] =
		externalIvySettingsURI(Def.value(url.toURI), addMultiResolver)
	def externalIvySettingsURI(uri: Initialize[URI], addMultiResolver: Boolean = true): Setting[Task[IvyConfiguration]] =
	{
		val other = (baseDirectory, appConfiguration, projectResolver, streams).identityMap
		ivyConfiguration <<= (uri zipWith other) { case (u, otherTask) =>
			otherTask map { case (base, app, pr, s) =>
				val extraResolvers = if(addMultiResolver) pr :: Nil else Nil
				new ExternalIvyConfiguration(base, u, Some(lock(app)), extraResolvers, s.log) }
		}
	}
	private[this] def inBase(name: String): Initialize[File] = Def.setting { baseDirectory.value / name }
	
	def externalIvyFile(file: Initialize[File] = inBase("ivy.xml"), iScala: Initialize[Option[IvyScala]] = ivyScala): Setting[Task[ModuleSettings]] =
		moduleSettings := new IvyFileConfiguration(file.value, iScala.value, ivyValidate.value, managedScalaInstance.value)
	def externalPom(file: Initialize[File] = inBase("pom.xml"), iScala: Initialize[Option[IvyScala]] = ivyScala): Setting[Task[ModuleSettings]] =
		moduleSettings := new PomConfiguration(file.value, ivyScala.value, ivyValidate.value, managedScalaInstance.value)

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
				(t, rTask) map { case ((cp, s, args), r) =>
					toError(r.run(mainClass, data(cp), baseArguments ++ args, s.log))
				}
			}
		}
	def fullRunTask(scoped: TaskKey[Unit], config: Configuration, mainClass: String, arguments: String*): Setting[Task[Unit]] =
		scoped <<= ( initScoped(scoped.scopedKey, runnerInit) zipWith (fullClasspath in config, streams).identityMap ) { case (rTask, t) =>
			(t, rTask) map { case ((cp, s), r) =>
				toError(r.run(mainClass, data(cp), arguments, s.log))
			}
		}
	def initScoped[T](sk: ScopedKey[_], i: Initialize[T]): Initialize[T]  =  initScope(fillTaskAxis(sk.scope, sk.key), i)
	def initScope[T](s: Scope, i: Initialize[T]): Initialize[T]  =  i mapReferenced Project.mapScope(Scope.replaceThis(s))

	/** Disables post-compilation hook for determining tests for tab-completion (such as for 'test-only').
	* This is useful for reducing test:compile time when not running test. */
	def noTestCompletion(config: Configuration = Test): Setting[_]  =  inConfig(config)( Seq(definedTests <<= detectTests) ).head

	def filterKeys(ss: Seq[Setting[_]], transitive: Boolean = false)(f: ScopedKey[_] => Boolean): Seq[Setting[_]] =
		ss filter ( s => f(s.key) && (!transitive || s.dependencies.forall(f)) )
}
trait BuildCommon
{
	@deprecated("Use Def.inputTask with the `Def.spaceDelimited()` parser.", "0.13.0")
	def inputTask[T](f: TaskKey[Seq[String]] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		InputTask.apply(Def.value((s: State) => Def.spaceDelimited()))(f)

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
		def files: Seq[File] = Attributed.data(s)
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
		loadForParserI(task)(Def value f)(format)
	def loadForParserI[P,T](task: TaskKey[T])(init: Initialize[(State, Option[T]) => Parser[P]])(implicit format: sbinary.Format[T]): Initialize[State => Parser[P]] =
		(resolvedScoped, init)( (ctx, f) => (s: State) => f( s, loadFromContext(task, ctx, s)(format)) )

	def getForParser[P,T](task: TaskKey[T])(init: (State, Option[T]) => Parser[P]): Initialize[State => Parser[P]] =
		getForParserI(task)(Def value init)
	def getForParserI[P,T](task: TaskKey[T])(init: Initialize[(State, Option[T]) => Parser[P]]): Initialize[State => Parser[P]] =
		(resolvedScoped, init)( (ctx, f) => (s: State) => f(s, getFromContext(task, ctx, s)) )

		// these are for use for constructing Tasks
	def loadPrevious[T](task: TaskKey[T])(implicit f: sbinary.Format[T]): Initialize[Task[Option[T]]] =
		(state, resolvedScoped) map { (s, ctx) => loadFromContext(task, ctx, s)(f) }

	def getPrevious[T](task: TaskKey[T]): Initialize[Task[Option[T]]] =
		(state, resolvedScoped) map { (s, ctx) => getFromContext(task, ctx, s) }
}
