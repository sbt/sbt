/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import Build.data
	import Scope.{GlobalScope, ThisScope}
	import compiler.Discovery
	import Project.{inConfig, Initialize, inScope, inTask, ScopedKey, Setting}
	import Configurations.{Compile => CompileConf, Test => TestConf}
	import EvaluateTask.resolvedScoped
	import complete._
	import std.TaskExtra._

	import scala.xml.{Node => XNode,NodeSeq}
	import org.apache.ivy.core.module.{descriptor, id}
	import descriptor.ModuleDescriptor, id.ModuleRevisionId
	import java.io.File
	import java.net.URL

	import Types._
	import Path._
	import GlobFilter._
	import Keys._

object Defaults
{
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
			in => f(in).getFiles
	}
	def configSrcSub(key: ScopedSetting[File]): Initialize[File] = (key, configuration) { (src, conf) => src / nameForSrc(conf.name) }
	def nameForSrc(config: String) = if(config == "compile") "main" else config
	def prefix(config: String) = if(config == "compile") "" else config + "-"
	def toSeq[T](key: ScopedSetting[T]): Initialize[Seq[T]] = key( _ :: Nil)

	def extractAnalysis[T](a: Attributed[T]): (T, inc.Analysis) = 
		(a.data, a.metadata get Keys.analysis getOrElse inc.Analysis.Empty)

	def analysisMap[T](cp: Seq[Attributed[T]]): Map[T, inc.Analysis] =
		(cp map extractAnalysis).toMap

	def buildCore: Seq[Setting[_]] = thisBuildCore ++ globalCore
	def thisBuildCore: Seq[Setting[_]] = inScope(GlobalScope.copy(project = Select(ThisBuild)))(Seq(
		managedDirectory <<= baseDirectory(_ / "lib_managed")
	))
	def globalCore: Seq[Setting[_]] = inScope(GlobalScope)(Seq(
		pollInterval :== 500,
		scalaHome :== None,
		javaHome :== None,
		outputStrategy :== None,
		fork :== false,
		javaOptions :== Nil,
		sbtPlugin :== false,
		crossPaths :== true,
		generatedResources :== Nil,
//		shellPrompt :== (_ => "> "),
		aggregate :== Aggregation.Enabled,
		maxErrors :== 100,
		showTiming :== true,
		timingFormat :== Aggregation.defaultFormat,
		showSuccess :== true,
		commands :== Nil,
		retrieveManaged :== false,
		settings <<= EvaluateTask.state map { state => Project.structure(state).data }
	))
	def projectCore: Seq[Setting[_]] = Seq(
		name <<= thisProject(_.id),
		version :== "0.1"	
	)
	def paths = Seq(
		baseDirectory <<= thisProject(_.base),
		target <<= baseDirectory / "target",
		defaultExcludes in GlobalScope :== (".*"  - ".") || HiddenFileFilter,
		historyPath <<= target(t => Some(t / ".history")),
		cacheDirectory <<= target / "cache",
		sourceDirectory <<= baseDirectory / "src",
		sourceFilter in GlobalScope :== ("*.java" | "*.scala"),
		sourceManaged <<= baseDirectory / "src_managed"
	)

	lazy val configPaths = Seq(
		sourceDirectory <<= configSrcSub( sourceDirectory in Scope(This,Global,This,This) ),
		sourceManaged <<= configSrcSub(sourceManaged),
		cacheDirectory <<= (cacheDirectory, configuration) { _ / _.name },
		classDirectory <<= (target, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "classes") },
		docDirectory <<= (target, configuration) { (outDir, conf) => outDir / (prefix(conf.name) + "api") },
		sources <<= (sourceDirectories, sourceFilter, defaultExcludes) map { (d,f,excl) => d.descendentsExcept(f,excl).getFiles },
		scalaSource <<= sourceDirectory / "scala",
		javaSource <<= sourceDirectory / "java",
		resourceDirectory <<= sourceDirectory / "resources",
		generatedResourceDirectory <<= target / "res_managed",
		sourceDirectories <<= (scalaSource, javaSource) { _ :: _ :: Nil },
		resourceDirectories <<= (resourceDirectory, generatedResourceDirectory) { _ :: _ :: Nil },
		resources <<= (resourceDirectories, defaultExcludes, generatedResources, generatedResourceDirectory) map resourcesTask,
		generatedResources <<= (definedSbtPlugins, generatedResourceDirectory) map writePluginsDescriptor
	)
	def addBaseSources = Seq(
		sources <<= (sources, baseDirectory, sourceFilter, defaultExcludes) map { (srcs,b,f,excl) => (srcs +++ b * (f -- excl)).getFiles }
	)
	
	def compileBase = Seq(
		classpathOptions in GlobalScope :== ClasspathOptions.auto,
		compilers <<= (scalaInstance, appConfiguration, streams, classpathOptions, javaHome) map { (si, app, s, co, jh) => Compiler.compilers(si, co, jh)(app, s.log) },
		javacOptions in GlobalScope :== Nil,
		scalacOptions in GlobalScope :== Nil,
		scalaInstance <<= scalaInstanceSetting,
		scalaVersion <<= appConfiguration( _.provider.scalaProvider.version),
		target <<= (target, scalaInstance, crossPaths)( (t,si,cross) => if(cross) t / ("scala-" + si.actualVersion) else t )
	)

	lazy val configTasks = Seq(
		initialCommands in GlobalScope :== "",
		compile <<= compileTask,
		compileInputs <<= compileInputsTask,
		console <<= consoleTask,
		consoleQuick <<= consoleQuickTask,
		discoveredMainClasses <<= compile map discoverMainClasses,
		definedSbtPlugins <<= discoverPlugins,
		inTask(run)(runnerSetting :: Nil).head,
		selectMainClass <<= discoveredMainClasses map selectRunMain,
		mainClass in run :== selectMainClass,
		mainClass <<= discoveredMainClasses map selectPackageMain,
		run <<= runTask(fullClasspath, mainClass in run, runner in run),
		scaladocOptions <<= scalacOptions(identity),
		doc <<= docTask,
		copyResources <<= copyResourcesTask
	)

	lazy val projectTasks: Seq[Setting[_]] = Seq(
		cleanFiles <<= (target, sourceManaged) { _ :: _ :: Nil },
		clean <<= cleanFiles map IO.delete,
		consoleProject <<= consoleProjectTask,
		watchSources <<= watchSourcesTask,
		watchTransitiveSources <<= watchTransitiveSourcesTask,
		watch <<= watchSetting
	)

	def inAllConfigurations[T](key: ScopedTask[T]): Initialize[Task[Seq[T]]] = (EvaluateTask.state, thisProjectRef) flatMap { (state, ref) =>
		val structure = Project structure state
		val configurations = Project.getProject(ref, structure).toList.flatMap(_.configurations)
		configurations.flatMap { conf =>
			key in (ref, conf) get structure.data
		} join
	}
	def watchTransitiveSourcesTask: Initialize[Task[Seq[File]]] =
		(EvaluateTask.state, thisProjectRef) flatMap { (s, base) =>
			inAllDependencies(base, watchSources.setting, Project structure s).join.map(_.flatten)
		}
	def watchSourcesTask: Initialize[Task[Seq[File]]] = Seq(sources, resources).map(inAllConfigurations).join { _.join.map(_.flatten.flatten) }

	def watchSetting: Initialize[Watched] = (pollInterval, thisProjectRef) { (interval, base) =>
		new Watched {
			val scoped = watchTransitiveSources in base
			val key = ScopedKey(scoped.scope, scoped.key)
			override def pollInterval = interval
			override def watchPaths(s: State) = EvaluateTask.evaluateTask(Project structure s, key, s, base) match { case Some(Value(ps)) => ps; case _ => Nil }
		}
	}
	def scalaInstanceSetting = (appConfiguration, scalaVersion, scalaHome){ (app, version, home) =>
		val launcher = app.provider.scalaProvider.launcher
		home match {
			case None => ScalaInstance(version, launcher)
			case Some(h) => ScalaInstance(h, launcher)
		}
	}
	def resourcesTask(dirs: Seq[File], excl: FileFilter, gen: Seq[File], genDir: File) = gen ++ (dirs --- genDir).descendentsExcept("*",excl).getFiles

	lazy val testTasks = Seq(	
		testLoader <<= (fullClasspath, scalaInstance) map { (cp, si) => TestFramework.createTestLoader(data(cp), si) },
		testFrameworks in GlobalScope :== {
			import sbt.TestFrameworks._
			Seq(ScalaCheck, Specs, ScalaTest, ScalaCheckCompat, ScalaTestCompat, SpecsCompat, JUnit)
		},
		loadedTestFrameworks <<= (testFrameworks, streams, testLoader) map { (frameworks, s, loader) =>
			frameworks.flatMap(f => f.create(loader, s.log).map( x => (f,x)).toIterable).toMap
		},
		definedTests <<= (loadedTestFrameworks, compile, streams) map { (frameworkMap, analysis, s) =>
			val tests = Tests.discover(frameworkMap.values.toSeq, analysis, s.log)._1
			IO.writeLines(s.text(CompletionsID), tests.map(_.name).distinct)
			tests
		},
		testListeners <<= (streams in test) map ( s => TestLogger(s.log) :: Nil ),
		testOptions <<= testListeners map { listeners => Tests.Listeners(listeners) :: Nil },
		executeTests <<= (streams in test, loadedTestFrameworks, testOptions, testLoader, definedTests) flatMap {
			(s, frameworkMap, options, loader, discovered) => Tests(frameworkMap, loader, discovered, options, s.log)
		},
		test <<= (executeTests, streams) map { (results, s) => Tests.showResults(s.log, results) },
		testOnly <<= testOnlyTask
	)

	def testOnlyTask = 
	InputTask(resolvedScoped(testOnlyParser)) ( result =>  
		(streams, loadedTestFrameworks, testOptions, testLoader, definedTests, result) flatMap {
			case (s, frameworks, opts, loader, discovered, (tests, frameworkOptions)) =>
				val modifiedOpts = Tests.Filter(if(tests.isEmpty) _ => true else tests.toSet ) +: Tests.Argument(frameworkOptions : _*) +: opts
				Tests(frameworks, loader, discovered, modifiedOpts, s.log) map { results =>
					Tests.showResults(s.log, results)
				}
		}
	)

	lazy val packageBase = Seq(
		jarNameSetting,
		packageOptions in GlobalScope :== Nil,
		nameToString in GlobalScope :== (ArtifactName.show _)
	)
	lazy val packageConfig = Seq(
		jarName <<= (jarName, configuration) { (n,c) => n.copy(config = c.name) },
		packageOptions in packageBin <<= (packageOptions, mainClass in packageBin) map { _ ++ _.map(Package.MainClass.apply).toList }
	) ++
	packageTasks(packageBin, "", packageBinTask) ++
	packageTasks(packageSrc, "src", packageSrcTask) ++
	packageTasks(packageDoc, "doc", packageDocTask)

	private[this] val allSubpaths = (dir: File) => (dir.*** --- dir) x relativeTo(dir)

	def packageBinTask = classMappings
	def packageDocTask = doc map allSubpaths
	def packageSrcTask = concat(resourceMappings, sourceMappings)

	private type Mappings = Initialize[Task[Seq[(File, String)]]]
	def concat(as: Mappings, bs: Mappings) = (as zipWith bs)( (a,b) => (a :^: b :^: KNil) map { case a :+: b :+: HNil => a ++ b } )
	def classMappings = (compileInputs, products) map { (in, _) => allSubpaths(in.config.classesDirectory) }
	// drop base directories, since there are no valid mappings for these
	def sourceMappings = (sources, sourceDirectories, baseDirectory) map { (srcs, sdirs, base) =>
		 ( (srcs --- sdirs --- base) x (relativeTo(sdirs)|relativeTo(base))) toSeq
	}
	def resourceMappings = (resources, resourceDirectories) map { (rs, rdirs) =>
		(rs --- rdirs) x relativeTo(rdirs) toSeq
	}
	
	def jarNameSetting  =  jarName <<= (moduleID, version, scalaVersion, crossPaths) { (n,v, sv, withCross) =>
		ArtifactName(base = n, version = v, config = "", tpe = "", ext = "jar", cross = if(withCross) sv else "")
	}
	def jarPathSetting  =  jarPath <<= (target, jarName, nameToString) { (t, n, toString) => t / toString(n) }

	def packageTasks(key: TaskKey[Package.Configuration], tpeString: String, mappingsTask: Initialize[Task[Seq[(File,String)]]]) =
		inTask(key)( Seq(
			key in ThisScope.copy(task = Global) <<= packageTask,
			mappings <<= mappingsTask,
			jarType :== tpeString,
			jarName <<= (jarType,jarName){ (tpe, name) => (name.copy(tpe = tpe)) },
			cacheDirectory <<= cacheDirectory / key.key.label,
			jarPathSetting
		))
	def packageTask: Initialize[Task[Package.Configuration]] =
		(jarPath, mappings, packageOptions, cacheDirectory, streams) map { (jar, srcs, options, cacheDir, s) =>
			val config = new Package.Configuration(srcs, jar, options)
			Package(config, cacheDir, s.log)
			config
		}

	def selectRunMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(Some(SimpleReader readLine _), classes)
	def selectPackageMain(classes: Seq[String]): Option[String] =
		sbt.SelectMainClass(None, classes)

	def runTask(classpath: ScopedTask[Classpath], mainClassTask: ScopedTask[Option[String]], scalaRun: ScopedSetting[ScalaRun]): Initialize[InputTask[Unit]] =
		InputTask(_ => complete.Parsers.spaceDelimited("<arg>")) { result =>
			(classpath, mainClassTask, scalaRun, streams, result) map { (cp, main, runner, s, args) =>
				val mainClass = main getOrElse error("No main class detected.")
				runner.run(mainClass, data(cp), args, s.log) foreach error
			}
		}

	def runnerSetting =
		runner <<= (scalaInstance, baseDirectory, javaOptions, outputStrategy, fork, javaHome) { (si, base, options, strategy, forkRun, javaHomeDir) =>
			if(forkRun) {
				new ForkRun( ForkOptions(scalaJars = si.jars, javaHome = javaHomeDir, outputStrategy = strategy,
					runJVMOptions = options, workingDirectory = Some(base)) )
			} else
				new Run(si)
		}

	def docTask: Initialize[Task[File]] =
		(compileInputs, streams, docDirectory, configuration, scaladocOptions) map { (in, s, target, config, options) =>
			val d = new Scaladoc(in.config.maxErrors, in.compilers.scalac)
			d(nameForSrc(config.name), in.config.sources, in.config.classpath, target, options)(s.log)
			target
		}

	def mainRunTask = run <<= runTask(fullClasspath in Configurations.Runtime, mainClass in run, runner in run)

	def discoverMainClasses(analysis: inc.Analysis): Seq[String] =
		Discovery.applications(Tests.allDefs(analysis)) collect { case (definition, discovered) if(discovered.hasMain) => definition.name }

	def consoleProjectTask = (EvaluateTask.state, streams, initialCommands in consoleProject) map { (state, s, extra) => Console.sbt(state, extra)(s.log); println() }
	def consoleTask: Initialize[Task[Unit]] = consoleTask(fullClasspath, console)
	def consoleQuickTask = consoleTask(externalDependencyClasspath, consoleQuick)
	def consoleTask(classpath: TaskKey[Classpath], task: TaskKey[_]): Initialize[Task[Unit]] = (compilers, classpath, scalacOptions in task, initialCommands in task, streams) map {
		(cs, cp, options, initCommands, s) =>
			(new Console(cs.scalac))(data(cp), options, initCommands, s.log).foreach(msg => error(msg))
			println()
	}
	
	def compileTask = (compileInputs, streams) map { (i,s) => Compiler(i,s.log) }
	def compileInputsTask =
		(dependencyClasspath, sources, compilers, javacOptions, scalacOptions, cacheDirectory, classDirectory, streams) map {
		(cp, srcs, cs, javacOpts, scalacOpts, cacheDir, classes, s) =>
			val classpath = classes +: data(cp)
			val analysis = analysisMap(cp)
			val cache = cacheDir / "compile"
			Compiler.inputs(classpath, srcs, classes, scalacOpts, javacOpts, analysis, cache, 100)(cs, s.log)
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
		val mappings = (resrcs --- dirs) x rebase(dirs, target)
		s.log.debug("Copy resource mappings: " + mappings.mkString("\n\t","\n\t",""))
		Sync(cacheFile)( mappings )
		mappings
	}

	def testOnlyParser(resolved: ScopedKey[_]): State => Parser[(Seq[String],Seq[String])] =
	{ state =>
			import DefaultParsers._
		def distinctParser(exs: Set[String]): Parser[Seq[String]] =
			token(Space ~> (NotSpace - "--").examples(exs) ).flatMap(ex => distinctParser(exs - ex).map(ex +: _)) ?? Nil
		val tests = savedLines(state, resolved, definedTests)
		val selectTests = distinctParser(tests.toSet) // todo: proper IDs
		val options = (token(Space ~> "--") ~> spaceDelimited("<option>")) ?? Nil
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

	lazy val baseClasspaths = Classpaths.publishSettings ++ Classpaths.baseSettings
	lazy val configSettings = Classpaths.configSettings ++ configTasks ++ configPaths ++ packageConfig

	lazy val compileSettings = configSettings ++ (mainRunTask +: addBaseSources)
	lazy val testSettings = configSettings ++ testTasks

	lazy val itSettings = inConfig(Configurations.IntegrationTest)(testSettings)
	lazy val defaultConfigs = inConfig(CompileConf)(compileSettings) ++ inConfig(TestConf)(testSettings)

	lazy val defaultSettings: Seq[Setting[_]] = projectCore ++ paths ++ baseClasspaths ++ baseTasks ++ compileBase ++ defaultConfigs ++ disableAggregation
}
object Classpaths
{
		import Path._
		import GlobFilter._
		import Keys._
		import Scope.ThisScope
		import Defaults._
		import Attributed.{blank, blankSeq}

	def concat[T](a: ScopedTaskable[Seq[T]], b: ScopedTaskable[Seq[T]]): Initialize[Task[Seq[T]]] = (a,b) map (_ ++ _)

	lazy val configSettings: Seq[Setting[_]] = Seq(
		externalDependencyClasspath <<= concat(unmanagedClasspath, managedClasspath),
		dependencyClasspath <<= concat(internalDependencyClasspath, externalDependencyClasspath),
		fullClasspath <<= concat(products, dependencyClasspath),
		internalDependencyClasspath <<= internalDependencies,
		unmanagedClasspath <<= unmanagedDependencies,
		products <<= makeProducts,
		managedClasspath <<= (configuration, update) map managedJars,
		unmanagedJars <<= (configuration, unmanagedBase, classpathFilter, defaultExcludes) map { (config, base, filter, excl) =>
			(base * (filter -- excl) +++ (base / config.name).descendentsExcept(filter, excl)).getFiles
		}
	)
	def defaultPackageTasks: Seq[ScopedTask[_]] =
		for(task <- Seq(packageBin, `packageSrc`, `packageDoc`); conf <- Seq(CompileConf, TestConf)) yield (task in conf)

	val publishSettings: Seq[Setting[_]] = Seq(
		publishMavenStyle in GlobalScope :== true,
		packageToPublish <<= defaultPackageTasks.dependOn,
		deliverDepends <<= (publishMavenStyle, makePom.setting, packageToPublish.setting) { (mavenStyle, mkpom, ptp) =>
			if(mavenStyle) mkpom.map(_ => ()) else ptp
		},
		makePom <<= (ivyModule, makePomConfiguration, packageToPublish, streams) map { (module, config, _, s) => IvyActions.makePom(module, config, s.log); config.file },
		deliver <<= deliverTask(publishConfiguration),
		deliverLocal <<= deliverTask(publishLocalConfiguration),
		publish <<= publishTask(publishConfiguration, deliver),
		publishLocal <<= publishTask(publishLocalConfiguration, deliverLocal)
	)
	val baseSettings: Seq[Setting[_]] = Seq(
		unmanagedBase <<= baseDirectory / "lib",
		normalizedName <<= name(StringUtilities.normalize),
		organization :== normalizedName,
		classpathFilter in GlobalScope :== "*.jar",
		fullResolvers <<= (projectResolver,resolvers,sbtPlugin,sbtResolver) map { (pr,rs,isPlugin,sr) =>
			val base = pr +: Resolver.withDefaultResolvers(rs)
			if(isPlugin) sr +: base else base
		},
		offline in GlobalScope :== false,
		moduleID :== normalizedName,
		defaultConfiguration in GlobalScope :== Some(Configurations.Compile),
		defaultConfigurationMapping in GlobalScope <<= defaultConfiguration{ case Some(d) => "*->" + d.name; case None => "*->*" },
		ivyPaths <<= (baseDirectory, appConfiguration) { (base, app) => new IvyPaths(base, Option(app.provider.scalaProvider.launcher.ivyHome)) },
		otherResolvers in GlobalScope :== Nil,
		projectResolver <<= projectResolverTask,
		projectDependencies <<= projectDependenciesTask,
		libraryDependencies in GlobalScope :== Nil,
		allDependencies <<= (projectDependencies,libraryDependencies,sbtPlugin,sbtDependency) map { (projDeps, libDeps, isPlugin, sbtDep) =>
			val base = projDeps ++ libDeps
			if(isPlugin) sbtDep +: base else base
		},
		ivyLoggingLevel in GlobalScope :== UpdateLogging.Quiet,
		ivyXML in GlobalScope :== NodeSeq.Empty,
		ivyValidate in GlobalScope :== false,
		ivyScala in GlobalScope <<= scalaVersion(v => Some(new IvyScala(v, Nil, false, false))),
		moduleConfigurations in GlobalScope :== Nil,
		publishTo in GlobalScope :== None,
		pomName <<= (moduleID, version, scalaVersion, crossPaths) { (n,v,sv, withCross) =>
			ArtifactName(base = n, version = v, config = "", tpe = "", ext = "pom", cross = if(withCross) sv else "")
		},
		pomFile <<= (target, pomName, nameToString) { (t, n, toString) => t / toString(n) },
		pomArtifact <<= (publishMavenStyle, moduleID)( (mavenStyle, name) => if(mavenStyle) Artifact(name, "pom", "pom") :: Nil else Nil),
		artifacts <<= (pomArtifact,moduleID)( (pom,name) => Artifact(name) +: pom),
		projectID <<= (organization,moduleID,version,artifacts){ (org,module,version,as) => ModuleID(org, module, version).cross(true).artifacts(as : _*) },
		resolvers in GlobalScope :== Nil,
		projectDescriptors <<= depMap,
		retrievePattern :== "[type]/[organisation]/[module]/[artifact](-[revision])(-[classifier]).[ext]",
		updateConfiguration <<= (retrieveConfiguration, ivyLoggingLevel)((conf,level) => new UpdateConfiguration(conf, false, level) ),
		retrieveConfiguration <<= (managedDirectory, retrievePattern, retrieveManaged) { (libm, pattern, enabled) => if(enabled) Some(new RetrieveConfiguration(libm, pattern)) else None },
		ivyConfiguration <<= (fullResolvers, ivyPaths, otherResolvers, moduleConfigurations, offline, appConfiguration) map { (rs, paths, other, moduleConfs, off, app) =>
			// todo: pass logger from streams directly to IvyActions
			val lock = app.provider.scalaProvider.launcher.globalLock
			new InlineIvyConfiguration(paths, rs, other, moduleConfs, off, Some(lock), ConsoleLogger())
		},
		moduleSettings <<= (projectID, allDependencies, ivyXML, thisProject, defaultConfiguration, ivyScala, ivyValidate) map {
			(pid, deps, ivyXML, project, defaultConf, ivyS, validate) => new InlineConfiguration(pid, deps, ivyXML, project.configurations, defaultConf, ivyS, validate)
		},
		makePomConfiguration <<= pomFile(file => makePomConfigurationTask(file)),
		publishConfiguration <<= (target, publishTo, ivyLoggingLevel) map { (outputDirectory, publishTo, level) => publishConfig( publishPatterns(outputDirectory), resolverName = getPublishTo(publishTo).name, logging = level) },
		publishLocalConfiguration <<= (target, ivyLoggingLevel) map { (outputDirectory, level) => publishConfig( publishPatterns(outputDirectory, true), logging = level ) },
		ivySbt <<= ivyConfiguration map { conf => new IvySbt(conf) },
		ivyModule <<= (ivySbt, moduleSettings) map { (ivySbt, settings) => new ivySbt.Module(settings) },
		update <<= (ivyModule, updateConfiguration, cacheDirectory, streams) map { (module, config, cacheDirectory, s) =>
			cachedUpdate(cacheDirectory / "update", module, config, s.log)
		},
		transitiveClassifiers :== Seq("sources", "javadoc"),
		updateClassifiers <<= (ivySbt, projectID, update, transitiveClassifiers, updateConfiguration, ivyScala, streams) map { (is, pid, up, classifiers, c, ivyScala, s) =>
			IvyActions.transitive(is, pid, up, classifiers, c, ivyScala, s.log)
		},
		updateSbtClassifiers <<= (ivySbt, projectID, transitiveClassifiers, updateConfiguration, sbtDependency, ivyScala, streams) map { (is, pid, classifiers, c, sbtDep, ivyScala, s) =>
			IvyActions.transitiveScratch(is, pid, "sbt", sbtDep :: Nil, classifiers, c, ivyScala, s.log)
		},
		sbtResolver in GlobalScope :== dbResolver,
		sbtDependency in GlobalScope <<= appConfiguration { app =>
			val id = app.provider.id
			ModuleID(id.groupID, id.name, id.version, crossVersion = id.crossVersioned)
		}
	)


	def deliverTask(config: TaskKey[PublishConfiguration]): Initialize[Task[Unit]] =
		(ivyModule, config, deliverDepends, streams) map { (module, config, _, s) => IvyActions.deliver(module, config, s.log) }
	def publishTask(config: TaskKey[PublishConfiguration], deliverKey: TaskKey[_]): Initialize[Task[Unit]] =
		(ivyModule, config, deliverKey, streams) map { (module, config, _, s) => IvyActions.publish(module, config, s.log) }

		import Cache._
		import CacheIvy.{classpathFormat, publishIC, updateIC, updateReportF}

	def cachedUpdate(cacheFile: File, module: IvySbt#Module, config: UpdateConfiguration, log: Logger): UpdateReport =
	{
		implicit val updateCache = updateIC
		implicit val updateReport = updateReportF
		type In = IvyConfiguration :+: ModuleSettings :+: UpdateConfiguration :+: HNil
		def work = (_:  In) match { case conf :+: settings :+: config :+: HNil =>
			log.info("Updating...")
			val r = IvyActions.update(module, config, log)
			log.info("Done updating.")
			r
		}

		val f =
			Tracked.inputChanged(cacheFile / "inputs") { (inChanged: Boolean, in: In) =>
				val outCache = Tracked.lastOutput[In, UpdateReport](cacheFile / "output") {
					case (_, Some(out)) if !inChanged && allFiles(out).forall(_.exists) => out
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
	def makePomConfigurationTask(file: File, configurations: Option[Iterable[Configuration]] = None, extra: NodeSeq = NodeSeq.Empty, process: XNode => XNode = n => n, filterRepositories: MavenRepository => Boolean = _ => true) = 
		new MakePomConfiguration(file, configurations, extra, process, filterRepositories)

	def getPublishTo(repo: Option[Resolver]): Resolver = repo getOrElse error("Repository for publishing is not specified.")

	def publishConfig(patterns: PublishPatterns, resolverName: String = "local", status: String = "release", logging: UpdateLogging.Value = UpdateLogging.DownloadOnly) =
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
			(mods.map{ mod =>
				val md = mod.moduleDescriptor(log)
				(md.getModuleRevisionId, md)
			}).toMap
		}

	def projectResolverTask: Initialize[Task[Resolver]] =
		projectDescriptors map { m =>
			new RawRepository(new ProjectResolver("inter-project", m))
		}

	def analyzed[T](data: T, analysis: inc.Analysis) = Attributed.blank(data).put(Keys.analysis, analysis)
	def makeProducts: Initialize[Task[Classpath]] =
		(compile, compileInputs, copyResources) map { (analysis, i, _) => analyzed(i.config.classesDirectory, analysis) :: Nil }

	def internalDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, thisProject, configuration, settings) flatMap internalDependencies0
	def unmanagedDependencies: Initialize[Task[Classpath]] =
		(thisProjectRef, thisProject, configuration, settings) flatMap unmanagedDependencies0

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
			val masterConfs = configurationNames(project)

			for( Project.ResolvedClasspathDependency(dep, confMapping) <- project.dependencies)
			{
				val depProject = thisProject in dep get data getOrElse error("Invalid project: " + dep)
				val mapping = mapped(confMapping, masterConfs, configurationNames(depProject), "compile", "*->compile")
				// map master configuration 'c' and all extended configurations to the appropriate dependency configuration
				for(ac <- applicableConfigs; depConfName <- mapping(ac.name))
				{
					val depConf = getConfiguration(dep, depProject, depConfName)
					if( ! visited( (dep, depConfName) ) )
						visit(dep, depProject, depConf)
				}
			}
		}
		visit(projectRef, project, conf)
		visited.toSeq
	}
	def unmanagedDependencies0(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, data, true, unmanagedLibs)
	def internalDependencies0(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, data: Settings[Scope]): Task[Classpath] =
		interDependencies(projectRef, project, conf, data, false, productsTask)
	def interDependencies(projectRef: ProjectRef, project: ResolvedProject, conf: Configuration, data: Settings[Scope], includeSelf: Boolean,
		f: (ProjectRef, String, Settings[Scope]) => Task[Classpath]): Task[Classpath] =
	{
		val visited = interSort(projectRef, project, conf, data)
		val tasks = asScalaSet(new LinkedHashSet[Task[Classpath]])
		for( (dep, c) <- visited )
			if(includeSelf || (dep != projectRef) || conf.name != c )
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

	def configurationNames(p: ResolvedProject): Seq[String] = p.configurations.map( _.name)
	def parseList(s: String, allConfs: Seq[String]): Seq[String] = (trim(s split ",") flatMap replaceWildcard(allConfs)).distinct
	def replaceWildcard(allConfs: Seq[String])(conf: String): Seq[String] =
		if(conf == "") Nil else if(conf == "*") allConfs else conf :: Nil
	
	private def trim(a: Array[String]): List[String] = a.toList.map(_.trim)
	def missingConfiguration(in: String, conf: String) =
		error("Configuration '" + conf + "' not defined in '" + in + "'")
	def allConfigs(conf: Configuration): Seq[Configuration] =
		Dag.topologicalSort(conf)(_.extendsConfigs)

	def getConfiguration(ref: ProjectRef, dep: ResolvedProject, conf: String): Configuration =
		dep.configurations.find(_.name == conf) getOrElse missingConfiguration(Project display ref, conf)
	def productsTask(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		getClasspath(products, dep, conf, data)
	def unmanagedLibs(dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		getClasspath(unmanagedJars, dep, conf, data)
	def getClasspath(key: TaskKey[Classpath], dep: ResolvedReference, conf: String, data: Settings[Scope]): Task[Classpath] =
		( key in (dep, ConfigKey(conf)) ) get data getOrElse const(Nil)
	def defaultConfigurationTask(p: ResolvedReference, data: Settings[Scope]): Configuration =
		flatten(defaultConfiguration in p get data) getOrElse Configurations.Default
	def flatten[T](o: Option[Option[T]]): Option[T] = o flatMap identity

	def managedJars(config: Configuration, up: UpdateReport): Classpath = managedFiles(config, up)(isJar)
	def allFiles(up: UpdateReport): Seq[File] = data( up.configurations flatMap { cr => allJars(cr)(_ => true) }).distinct
	def managedFiles(config: Configuration, up: UpdateReport)(pred: Artifact => Boolean): Classpath =
		allJars( confReport(config.name, up) )(pred)
	def confReport(config: String, up: UpdateReport): ConfigurationReport =
		up.configuration(config) getOrElse error("Configuration '" + config + "' unresolved by 'update'.")
	def allJars(cr: ConfigurationReport)(pred: Artifact => Boolean): Seq[File] = cr.modules.flatMap(mr => allJars(mr.artifacts)(pred))
	def allJars(as: Seq[(Artifact,File)])(pred: Artifact => Boolean): Seq[File] = as collect { case (a, f) if pred(a) => f }
	def isJar(a: Artifact): Boolean = a.`type` == "jar"
	
	lazy val dbResolver = Resolver.url("sbt-db", new URL("http://databinder.net/repo/"))(Resolver.ivyStylePatterns)
}
