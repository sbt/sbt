/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Build.data
	import Scope.{fillTaskAxis, GlobalScope, ThisScope}
	import xsbt.api.Discovery
	import xsbti.compile.CompileOrder
	import Project.{inConfig, inScope, inTask, richInitialize, richInitializeTask, richTaskSessionVar}
	import Def.{Initialize, ScopedKey, Setting, SettingsDefinition}
	import Artifact.{DocClassifier, SourceClassifier}
	import Configurations.{Compile, CompilerPlugin, IntegrationTest, names, Provided, Runtime, Test}
	import CrossVersion.{binarySbtVersion, binaryScalaVersion}
	import complete._
	import std.TaskExtra._
	import inc.{FileValueCache, Locate}
	import org.scalatools.testing.{Framework, AnnotatedFingerprint, SubclassFingerprint}

	import sys.error
	import scala.xml.NodeSeq
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import java.io.File
	import java.net.{URI,URL}
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
	def nameForSrc(config: String) = if(config == "compile") "main" else config
	def prefix(config: String) = if(config == "compile") "" else config + "-"

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
		managedDirectory <<= baseDirectory(_ / "lib_managed")
	))
	def globalCore: Seq[Setting[_]] = inScope(GlobalScope)(defaultTestTasks(test) ++ defaultTestTasks(testOnly) ++ defaultTestTasks(testQuick) ++ Seq(
		compilerCache <<= state map { _ get Keys.stateCompilerCache getOrElse compiler.CompilerCache.fresh },
		crossVersion :== CrossVersion.Disabled,
		scalaOrganization :== ScalaArtifacts.Organization,
		buildDependencies <<= buildDependencies or Classpaths.constructBuildDependencies,
		taskTemporaryDirectory := { val dir = IO.createTemporaryDirectory; dir.deleteOnExit(); dir },
		onComplete <<= taskTemporaryDirectory { dir => () => IO.delete(dir); IO.createDirectory(dir) },
		concurrentRestrictions <<= concurrentRestrictions or defaultRestrictions,
		parallelExecution :== true,
		sbtVersion <<= appConfiguration { _.provider.id.version },
		sbtBinaryVersion <<= sbtVersion apply binarySbtVersion,
		sbtResolver <<= sbtVersion { sbtV => if(sbtV endsWith "-SNAPSHOT") Classpaths.typesafeSnapshots else Classpaths.typesafeReleases },
		pollInterval :== 500,
		logBuffered :== false,
		connectInput :== false,
		cancelable :== false,
		sourcesInBase :== true,
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
		classpathTypes :== Set("jar", "bundle") ++ CustomPomParser.JarPackagings,
		aggregate :== true,
		maxErrors :== 100,
		sourcePositionMappers :== Nil,
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
		name <<= thisProject(_.id),
		logManager <<= extraLoggers(extra => LogManager.defaults(extra, StandardMain.console)),
		onLoadMessage <<= onLoadMessage or (name, thisProjectRef)("Set current project to " + _ + " (in build " + _.build +")"),
		runnerTask
	)
	def paths = Seq(
		baseDirectory <<= thisProject(_.base),
		target <<= baseDirectory / "target",
		historyPath <<= historyPath or target(t => Some(t / ".history")),
		sourceDirectory <<= baseDirectory / "src",
		sourceManaged <<= crossTarget / "src_managed",
		resourceManaged <<= crossTarget / "resource_managed",
		cacheDirectory <<= (crossTarget, thisProject)(_ / CacheDirectoryName / _.id / "global")
	)

	lazy val configPaths = sourceConfigPaths ++ resourceConfigPaths ++ outputConfigPaths
	lazy val sourceConfigPaths = Seq(
		sourceDirectory <<= configSrcSub(sourceDirectory),
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
		cacheDirectory <<= (crossTarget, thisProject, configuration) { _ / CacheDirectoryName / _.id / _.name },
		classDirectory <<= (crossTarget, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "classes") },
		docDirectory <<= (crossTarget, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "api") }
	)
	def addBaseSources = Seq(
		unmanagedSources <<= (unmanagedSources, baseDirectory, includeFilter in unmanagedSources, excludeFilter in unmanagedSources, sourcesInBase) map {
			(srcs,b,f,excl,enable) => if(enable) (srcs +++ b * (f -- excl)).get else srcs
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
		scalaBinaryVersion in GlobalScope <<= scalaVersion apply binaryScalaVersion,
		crossVersion <<= (crossPaths) { enabled => if(enabled) CrossVersion.binary else CrossVersion.Disabled },
		crossScalaVersions in GlobalScope <<= Seq(scalaVersion).join,
		crossTarget <<= (target, scalaBinaryVersion, sbtBinaryVersion, sbtPlugin, crossPaths)(makeCrossTarget)
	)
	def makeCrossTarget(t: File, sv: String, sbtv: String, plugin: Boolean, cross: Boolean): File =
	{
		val scalaBase = if(cross) t / ("scala-" + sv) else t
		if(plugin) scalaBase / ("sbt-" + sbtv) else scalaBase
	}
	def compilersSetting = compilers <<= (scalaInstance, appConfiguration, streams, classpathOptions, javaHome) map { (si, app, s, co, jh) => Compiler.compilers(si, co, jh)(app, s.log) }

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
		selectMainClass <<= (discoveredMainClasses, mainClass) map { (classes, explicit) => explicit orElse selectRunMain(classes) },
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
				case None => error("key not found: " + Def.displayFull(key))
			}
		}
	}
	def scalaInstanceSetting = (appConfiguration, scalaOrganization, scalaVersion, scalaHome) map { (app, org, version, home) =>
		val launcher = app.provider.scalaProvider.launcher
		home match {
			case None => ScalaInstance(org, version, launcher)
			case Some(h) => ScalaInstance(h, launcher)
		}
	}

	lazy val testTasks: Seq[Setting[_]] = testTaskOptions(test) ++ testTaskOptions(testOnly) ++ testTaskOptions(testQuick) ++ Seq(
		testLoader <<= (fullClasspath, scalaInstance, taskTemporaryDirectory) map { (cp, si, temp) => TestFramework.createTestLoader(data(cp), si, IO.createUniqueDirectory(temp)) },
		testFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs2, Specs, ScalaTest, JUnit)
		},
		loadedTestFrameworks <<= (testFrameworks, streams, testLoader) map { (frameworks, s, loader) =>
			frameworks.flatMap(f => f.create(loader, s.log).map( x => (f,x)).toIterable).toMap
		},
		definedTests <<= detectTests,
		definedTestNames <<= definedTests map ( _.map(_.name).distinct) storeAs definedTestNames triggeredBy compile,
		testListeners in GlobalScope :== Nil,
		testOptions in GlobalScope :== Nil,
		testFilter in testOnly :== (selectedFilter _),
		testFilter in testQuick <<= testQuickFilter,
		executeTests <<= (streams in test, loadedTestFrameworks, testLoader, testGrouping in test, testExecution in test, fullClasspath in test, javaHome in test) flatMap allTestGroupsTask,
		test <<= (executeTests, streams, resolvedScoped, state) map { 
			(results, s, scoped, st) =>
				implicit val display = Project.showContextKey(st)
				Tests.showResults(s.log, results, noTestsMessage(scoped))
		},
		testOnly <<= inputTests(testOnly),
		testQuick <<= inputTests(testQuick)
	)
	private[this] def noTestsMessage(scoped: ScopedKey[_])(implicit display: Show[ScopedKey[_]]): String =
		"No tests to run for " + display(scoped)

	lazy val TaskGlobal: Scope = ThisScope.copy(task = Global)
	lazy val ConfigGlobal: Scope = ThisScope.copy(config = Global)
	def testTaskOptions(key: Scoped): Seq[Setting[_]] = inTask(key)( Seq(
		testListeners <<= (streams, resolvedScoped, streamsManager, logBuffered, cacheDirectory in test, testListeners in TaskGlobal) map { (s, sco, sm, buff, dir, ls) =>
			TestLogger(s.log, testLogger(sm, test in sco.scope), buff) +: new TestStatusReporter(succeededFile(dir)) +: ls
		},
		testOptions <<= (testOptions in TaskGlobal, testListeners) map { (options, ls) => Tests.Listeners(ls) +: options },
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
	def singleTestGroup(key: Scoped): Initialize[Task[Seq[Tests.Group]]] =
		((definedTests in key, fork in key, javaOptions in key) map {
			(tests, fork, javaOpts) => Seq(new Tests.Group("<default>", tests, if (fork) Tests.SubProcess(javaOpts) else Tests.InProcess))
		})

	def testExecutionTask(task: Scoped): Initialize[Task[Tests.Execution]] =
			(testOptions in task, parallelExecution in task, tags in task) map {
				(opts, par, ts) =>
					new Tests.Execution(opts, par, ts)
			}

	def testQuickFilter: Initialize[Task[Seq[String] => String => Boolean]] =
	  (fullClasspath in test, cacheDirectory) map {
			(cp, dir) =>
				val ans = cp.flatMap(_.metadata get Keys.analysis)
				val succeeded = TestStatus.read(succeededFile(dir))
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
				args => test => selectedFilter(args)(test) && {
					succeeded.get(test) match {
						case None => true
						case Some(ts) => stamp(test) > ts
					}
				}
		}
	def succeededFile(dir: File) = dir / "succeeded_tests"

	def inputTests(key: InputKey[_]): Initialize[InputTask[Unit]] =
		InputTask( loadForParser(definedTestNames)( (s, i) => testOnlyParser(s, i getOrElse Nil) ) ) { result =>
			(streams, loadedTestFrameworks, testFilter in key, testGrouping in key, testExecution in key, testLoader, resolvedScoped, result, fullClasspath in key, javaHome in key, state) flatMap {
				case (s, frameworks, filter, groups, config, loader, scoped, (selected, frameworkOptions), cp, javaHome, st) =>
					implicit val display = Project.showContextKey(st)
					val modifiedOpts = Tests.Filter(filter(selected)) +: Tests.Argument(frameworkOptions : _*) +: config.options
					val newConfig = config.copy(options = modifiedOpts)
					allTestGroupsTask(s, frameworks, loader, groups, newConfig, cp, javaHome) map (Tests.showResults(s.log, _, noTestsMessage(scoped)))
			}
		}

	def allTestGroupsTask(s: TaskStreams, frameworks: Map[TestFramework,Framework], loader: ClassLoader, groups: Seq[Tests.Group], config: Tests.Execution,	cp: Classpath, javaHome: Option[File]): Task[Tests.Output] = {
		val groupTasks = groups map {
			case Tests.Group(name, tests, runPolicy) =>
				runPolicy match {
					case Tests.SubProcess(javaOpts) =>
						ForkTests(frameworks.keys.toSeq, tests.toList, config, cp.files, javaHome, javaOpts, s.log) tag Tags.ForkedTestGroup
					case Tests.InProcess =>
						Tests(frameworks, loader, tests, config, s.log)
				}
		}
		Tests.foldTasks(groupTasks, config.parallel)
	}

	def selectedFilter(args: Seq[String]): String => Boolean =
	{
		val filters = args map GlobFilter.apply
		s => filters.isEmpty || filters.exists { _ accept s }
	}
	def detectTests: Initialize[Task[Seq[TestDefinition]]] = (loadedTestFrameworks, compile, streams) map { (frameworkMap, analysis, s) =>
		Tests.discover(frameworkMap.values.toSeq, analysis, s.log)._1
	}
	def defaultRestrictions: Initialize[Seq[Tags.Rule]] = parallelExecution { par =>
		val max = EvaluateTask.SystemProcessors
		Tags.limitAll(if(par) max else 1) :: Tags.limit(Tags.ForkedTestGroup, 1) :: Nil
	}

	lazy val packageBase: Seq[Setting[_]] = Seq(
		artifact <<= moduleName(n => Artifact(n)),
		packageOptions in GlobalScope :== Nil,
		artifactName in GlobalScope :== ( Artifact.artifactName _ )
	)
	lazy val packageConfig: Seq[Setting[_]] =
		inTask(packageBin)(Seq(
			packageOptions <<= (name, version, homepage, organization, organizationName, mainClass, packageOptions) map { (name, ver, h, org, orgName, main, p) => Package.addSpecManifestAttributes(name, ver, orgName) +: Package.addImplManifestAttributes(name, ver, h, org, orgName) +: main.map(Package.MainClass.apply) ++: p })) ++
		inTask(packageSrc)(Seq(
			packageOptions <<= (name, version, organizationName, packageOptions) map { Package.addSpecManifestAttributes(_, _, _) +: _ })) ++
	packageTaskSettings(packageBin, packageBinMappings) ++
	packageTaskSettings(packageSrc, packageSrcMappings) ++
	packageTaskSettings(packageDoc, packageDocMappings) ++
	Seq(`package` <<= packageBin)

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
	@deprecated("Use `Util.pairID` instead", "0.12.0")
	def pairID = Util.pairID

	def perTaskCache(key: TaskKey[_]): Setting[File] =
		cacheDirectory ~= { _ / ("for_" + key.key.label) }

	@deprecated("Use `packageTaskSettings` instead", "0.12.0")
	def packageTasks(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) = packageTaskSettings(key, mappingsTask)
	def packageTaskSettings(key: TaskKey[File], mappingsTask: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in TaskGlobal <<= packageTask,
			packageConfiguration <<= packageConfigurationTask,
			mappings <<= mappingsTask,
			packagedArtifact <<= (artifact, key) map Util.pairID,
			artifact <<= artifactSetting,
			perTaskCache(key),
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
					new ForkRun( ForkOptions(scalaJars = si.jars, javaHome = javaHomeDir, connectInput = connectIn, outputStrategy = strategy, runJVMOptions = options, workingDirectory = Some(base)) )
				} else
					new Run(si, trap, tmp)
		}

	@deprecated("Use `docTaskSettings` instead", "0.12.0")
	def docSetting(key: TaskKey[File]) = docTaskSettings(key)
	def docTaskSettings(key: TaskKey[File] = doc): Seq[Setting[_]] = inTask(key)(compileInputsSettings ++ Seq(
		perTaskCache(key),
		target <<= docDirectory, // deprecate docDirectory in favor of 'target in doc'; remove when docDirectory is removed
		scalacOptions <<= scaladocOptions or scalacOptions, // deprecate scaladocOptions in favor of 'scalacOptions in doc'; remove when scaladocOptions is removed
		key in TaskGlobal <<= (cacheDirectory, compileInputs, target, configuration, streams) map { (cache, in, out, config, s) =>
			val srcs = in.config.sources
			val hasScala = srcs.exists(_.name.endsWith(".scala"))
			val hasJava = srcs.exists(_.name.endsWith(".java"))
			val cp = in.config.classpath.toList.filterNot(_ == in.config.classesDirectory)
			val label = nameForSrc(config.name)
			val (options, runDoc) = 
				if(hasScala)
					(in.config.options, Doc.scaladoc(label, cache / "scala", in.compilers.scalac))
				else if(hasJava)
					(in.config.javacOptions, Doc.javadoc(label, cache / "java", in.compilers.javac))
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
				val loader = sbt.classpath.ClasspathUtilities.makeLoader(data(cp), si.loader, si, IO.createUniqueDirectory(temp))
				(new Console(cs.scalac))(data(cp), options, loader, initCommands, cleanup)()(s.log).foreach(msg => error(msg))
				println()
		}

	def compileTaskSettings: Seq[Setting[_]] = inTask(compile)(compileInputsSettings)

	def compileTask = (compileInputs in compile, streams) map { (i,s) => Compiler(i,s.log) }
	def compileIncSetupTask =
		(dependencyClasspath, cacheDirectory, skip in compile, definesClass, compilerCache) map { (cp, cacheDir, skip, definesC, cache) =>
			Compiler.IncSetup(analysisMap(cp), definesC, skip, cacheDir / "inc_compile", cache)
		}
	def compileInputsSettings: Seq[Setting[_]] = {
		val optionsPair = TaskKey.local[(Seq[String], Seq[String])]
		Seq(optionsPair <<= (scalacOptions, javacOptions) map Util.pairID,
		compileInputs <<= (dependencyClasspath, sources, compilers, optionsPair, classDirectory, compileOrder, compileIncSetup, maxErrors, streams, sourcePositionMappers) map {
			(cp, srcs, cs, optsPair, classes, order, incSetup, maxErr, s, spms) =>
				Compiler.inputs(classes +: data(cp), srcs, classes, optsPair._1, optsPair._2, maxErr, spms, order)(cs, incSetup, s.log)
			})
	}
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
	(classDirectory, cacheDirectory, resources, resourceDirectories, streams) map { (target, cache, resrcs, dirs, s) =>
		val cacheFile = cache / "copy-resources"
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

	def inDependencies[T](key: SettingKey[T], default: ProjectRef => T, includeRoot: Boolean = true, classpath: Boolean = true, aggregate: Boolean = false): Initialize[Seq[T]] =
		forDependencies[T,T](ref => (key in ref) ?? default(ref), includeRoot, classpath, aggregate)

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
			(base * (filter -- excl) +++ (base / config.name).descendantsExcept(filter, excl)).classpath
		}
	)
	def defaultPackageKeys = Seq(packageBin, packageSrc, packageDoc)
	lazy val defaultPackages: Seq[TaskKey[File]] =
		for(task <- defaultPackageKeys; conf <- Seq(Compile, Test)) yield (task in conf)
	lazy val defaultArtifactTasks: Seq[TaskKey[File]] = makePom +: defaultPackages

	def findClasspathConfig(map: Configuration => Configuration, thisConfig: Configuration, delegate: Task[Option[Configuration]], up: Task[UpdateReport]): Task[Configuration] =
		(delegate, up) map { case (delegated, report) =>
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
		makePom <<= (ivyModule, makePomConfiguration, streams) map { (module, config, s) => IvyActions.makePom(module, config, s.log); config.file },
		packagedArtifact in makePom <<= (artifact in makePom, makePom) map Util.pairID,
		deliver <<= deliverTask(deliverConfiguration),
		deliverLocal <<= deliverTask(deliverLocalConfiguration),
		publish <<= publishTask(publishConfiguration, deliver),
		publishLocal <<= publishTask(publishLocalConfiguration, deliverLocal)
	)
	val baseSettings: Seq[Setting[_]] = sbtClassifiersTasks ++ Seq(
		conflictWarning in GlobalScope :== ConflictWarning.default("global"),
		conflictWarning <<= (thisProjectRef, conflictWarning) { (ref, cw) => cw.copy(label = Reference.display(ref)) },
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
		scmInfo in GlobalScope :== None,
		projectInfo <<= (name, description, homepage, startYear, licenses, organizationName, organizationHomepage, scmInfo) apply ModuleInfo,
		overrideBuildResolvers <<= appConfiguration(isOverrideRepositories),
		externalResolvers <<= (externalResolvers.task.?, resolvers) {
			case (Some(delegated), Seq()) => delegated
			case (_, rs) => task { Resolver.withDefaultResolvers(rs) }
		},
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
		ivyPaths <<= (baseDirectory, appConfiguration) { (base, app) => new IvyPaths(base, bootIvyHome(app)) },
		otherResolvers <<= publishTo(_.toList),
		projectResolver <<= projectResolverTask,
		projectDependencies <<= projectDependenciesTask,
		dependencyOverrides in GlobalScope :== Set.empty,
		libraryDependencies in GlobalScope :== Nil,
		libraryDependencies <++= (autoScalaLibrary, sbtPlugin, scalaVersion) apply autoLibraryDependency,
		allDependencies <<= (projectDependencies,libraryDependencies,sbtPlugin,sbtDependency) map { (projDeps, libDeps, isPlugin, sbtDep) =>
			val base = projDeps ++ libDeps
			if(isPlugin) sbtDep.copy(configurations = Some(Provided.name)) +: base else base
		},
		ivyLoggingLevel in GlobalScope :== UpdateLogging.DownloadOnly,
		ivyXML in GlobalScope :== NodeSeq.Empty,
		ivyValidate in GlobalScope :== false,
		ivyScala <<= ivyScala or (scalaHome, scalaVersion in update, scalaBinaryVersion in update) { (sh,fv,bv) =>
			Some(new IvyScala(fv, bv, Nil, filterImplicit = true, checkExplicit = true, overrideScalaVersion = sh.isEmpty))
		},
		moduleConfigurations in GlobalScope :== Nil,
		publishTo in GlobalScope :== None,
		artifactPath in makePom <<= artifactPathSetting(artifact in makePom),
		publishArtifact in makePom <<= (publishMavenStyle, publishArtifact).apply(_ && _) ,
		artifact in makePom <<= moduleName(Artifact.pom),
		projectID <<= (organization,moduleName,version,artifacts,crossVersion in projectID){ (org,module,version,as,cross) =>
			ModuleID(org, module, version).cross(cross).artifacts(as : _*)
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
		deliverLocalConfiguration <<= (crossTarget, isSnapshot, ivyLoggingLevel) map { (outDir, snapshot, level) => deliverConfig( outDir, status = if (snapshot) "integration" else "release", logging = level ) },
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
		update <<= (ivyModule, thisProjectRef, updateConfiguration, cacheDirectory, scalaInstance, transitiveUpdate, executionRoots, resolvedScoped, skip in update, streams) map {
			(module, ref, config, cacheDirectory, si, reports, roots, resolved, skip, s) =>
				val depsUpdated = reports.exists(!_.stats.cached)
				val isRoot = roots contains resolved
				cachedUpdate(cacheDirectory / "update", Reference.display(ref), module, config, Some(si), skip = skip, force = isRoot, depsUpdated = depsUpdated, log = s.log)
		} tag(Tags.Update, Tags.Network),
		update <<= (conflictWarning, update, streams) map { (config, report, s) => ConflictWarning(config, report, s.log); report },
		transitiveClassifiers in GlobalScope :== Seq(SourceClassifier, DocClassifier),
		classifiersModule in updateClassifiers <<= (projectID, update, transitiveClassifiers in updateClassifiers, ivyConfigurations in updateClassifiers) map { ( pid, up, classifiers, confs) =>
			GetClassifiersModule(pid, up.allModules, confs, classifiers)
		},
		updateClassifiers <<= (ivySbt, classifiersModule in updateClassifiers, updateConfiguration, ivyScala, target in LocalRootProject, appConfiguration, streams) map { (is, mod, c, ivyScala, out, app, s) =>
			withExcludes(out, mod.classifiers, lock(app)) { excludes =>
				IvyActions.updateClassifiers(is, GetClassifiersConfiguration(mod, excludes, c, ivyScala), s.log)
			}
		} tag(Tags.Update, Tags.Network),
		sbtDependency in GlobalScope <<= appConfiguration { app =>
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
	def pluginProjectID: Initialize[ModuleID] = (sbtBinaryVersion in update, scalaBinaryVersion in update, projectID, sbtPlugin) {
		(sbtBV, scalaBV, pid, isPlugin) =>
			if(isPlugin) sbtPluginExtra(pid, sbtBV, scalaBV) else pid
	}
	def ivySbt0: Initialize[Task[IvySbt]] =
		(ivyConfiguration, credentials, streams) map { (conf, creds, s) =>
			Credentials.register(creds, s.log)
			new IvySbt(conf)
		}
	def moduleSettings0: Initialize[Task[ModuleSettings]] =
		(projectID, allDependencies, dependencyOverrides, ivyXML, ivyConfigurations, defaultConfiguration, ivyScala, ivyValidate, projectInfo) map {
			(pid, deps, over, ivyXML, confs, defaultConf, ivyS, validate, pinfo) => new InlineConfiguration(pid, pinfo, deps, over, ivyXML, confs, defaultConf, ivyS, validate)
		}

	def sbtClassifiersTasks = inTask(updateSbtClassifiers)(Seq(
		transitiveClassifiers in GlobalScope in updateSbtClassifiers ~= ( _.filter(_ != DocClassifier) ),
		externalResolvers <<= (externalResolvers, appConfiguration, buildStructure, thisProjectRef) map { (defaultRs, ac, struct, ref) =>
			val explicit = struct.units(ref.build).unit.plugins.pluginData.resolvers
			explicit orElse bootRepositories(ac) getOrElse defaultRs
		},
		ivyConfiguration <<= (externalResolvers, ivyPaths, offline, checksums, appConfiguration, target, streams) map { (rs, paths, off, check, app, t, s) =>
			val resCacheDir = t / "resolution-cache"
			new InlineIvyConfiguration(paths, rs, Nil, Nil, off, Option(lock(app)), check, Some(resCacheDir), s.log)
		},
		ivySbt <<= ivySbt0,
		classifiersModule <<= (projectID, sbtDependency, transitiveClassifiers, loadedBuild, thisProjectRef) map { ( pid, sbtDep, classifiers, lb, ref) =>
			val pluginIDs: Seq[ModuleID] = lb.units(ref.build).unit.plugins.fullClasspath.flatMap(_ get moduleID.key)
			GetClassifiersModule(pid, sbtDep +: pluginIDs, Configurations.Default :: Nil, classifiers)
		},
		updateSbtClassifiers in TaskGlobal <<= (ivySbt, classifiersModule, updateConfiguration, ivyScala, target in LocalRootProject, appConfiguration, streams) map {
				(is, mod, c, ivyScala, out, app, s) =>
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

	def cachedUpdate(cacheFile: File, label: String, module: IvySbt#Module, config: UpdateConfiguration, scalaInstance: Option[ScalaInstance], skip: Boolean, force: Boolean, depsUpdated: Boolean, log: Logger): UpdateReport =
	{
		implicit val updateCache = updateIC
		type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil
		def work = (_:  In) match { case conf :+: settings :+: config :+: HNil =>
			log.info("Updating " + label + "...")
			val r = IvyActions.update(module, config, log)
			log.info("Done updating.")
			scalaInstance match { case Some(si) => substituteScalaFiles(si, r); case None => r }
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
		(thisProjectRef, settings, buildDependencies) map { (ref, data, deps) =>
			deps.classpath(ref) flatMap { dep => (projectID in dep.project) get data map { 
				_.copy(configurations = dep.configuration, explicitArtifacts = Nil) } 
			}
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

	def isOverrideRepositories(app: xsbti.AppConfiguration): Boolean =
		try app.provider.scalaProvider.launcher.isOverrideRepositories
		catch { case _: NoSuchMethodError => false }

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

	def seq(settings: Setting[_]*): SettingsDefinition = new Def.SettingList(settings)

	def externalIvySettings(file: Initialize[File] = baseDirectory / "ivysettings.xml", addMultiResolver: Boolean = true): Setting[Task[IvyConfiguration]] =
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
