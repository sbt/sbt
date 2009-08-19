/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah, David MacIver
 */
package sbt

import FileUtilities._
import java.io.File
import java.util.jar.{Attributes, Manifest}
import scala.collection.mutable.ListBuffer

trait SimpleScalaProject extends ExecProject
{
	def errorTask(message: String) = task{ Some(message) }
	
	trait CleanOption extends ActionOption
	case class ClearAnalysis(analysis: TaskAnalysis[_, _, _]) extends CleanOption
	case class Preserve(paths: PathFinder) extends CleanOption
	
	case class CompileOption(val asString: String) extends ActionOption
	case class JavaCompileOption(val asString: String) extends ActionOption
	
	val Deprecation = CompileOption("-deprecation")
	val ExplainTypes = CompileOption("-explaintypes")
	val Optimize = CompileOption("-optimise")
	def Optimise = Optimize
	val Verbose = CompileOption("-verbose")
	val Unchecked = CompileOption("-unchecked")
	val DisableWarnings = CompileOption("-nowarn")
	def target(target: Target.Value) = CompileOption("-target:" + target)
	object Target extends Enumeration
	{
		val Java1_5 = Value("jvm-1.5")
		val Java1_4 = Value("jvm-1.4")
		val Msil = Value("msil")
	}
	
	def cleanTask(paths: PathFinder, options: CleanOption*): Task =
		cleanTask(paths, options)
	def cleanTask(paths: PathFinder, options: => Seq[CleanOption]): Task =
		task
		{
			val cleanOptions = options
			val preservePaths = for(Preserve(preservePaths) <- cleanOptions; toPreserve <- preservePaths.get) yield toPreserve
			Control.thread(FileUtilities.preserve(preservePaths, log))
			{ preserved =>
				val pathClean = FileUtilities.clean(paths.get, log)
				for(ClearAnalysis(analysis) <- cleanOptions)
				{
					analysis.clear()
					analysis.save()
				}
				val restored = preserved.restore(log)
				pathClean orElse restored
			}
		}
}
trait ScalaProject extends SimpleScalaProject with FileTasks with MultiTaskProject with Exec
{
	import ScalaProject._
	
	final case class MaxCompileErrors(val value: Int) extends CompileOption("") with ScaladocOption { def asList = Nil }
	trait PackageOption extends ActionOption
	trait TestOption extends ActionOption
	
	case class TestSetup(setup: () => Option[String]) extends TestOption
	case class TestCleanup(cleanup: () => Option[String]) extends TestOption
	case class ExcludeTests(tests: Iterable[String]) extends TestOption
	case class TestListeners(listeners: Iterable[TestReportListener]) extends TestOption
	case class TestFilter(filterTest: String => Boolean) extends TestOption
	
	case class JarManifest(m: Manifest) extends PackageOption
	{
		assert(m != null)
	}
	case class MainClass(mainClassName: String) extends PackageOption
	case class ManifestAttributes(attributes: (Attributes.Name, String)*) extends PackageOption
	case object Recursive extends PackageOption
	def ManifestAttributes(attributes: (String, String)*): ManifestAttributes =
	{
		val converted = for( (name,value) <- attributes ) yield (new Attributes.Name(name), value)
		new ManifestAttributes(converted : _*)
	}
	
	
	trait ScaladocOption extends ActionOption
	{
		def asList: List[String]
	}
	case class SimpleDocOption(optionValue: String) extends ScaladocOption
	{
		def asList = List(optionValue)
	}
	case class CompoundDocOption(label: String, value: String) extends ScaladocOption
	{
		def asList = List(label, value)
	}
	val LinkSource = SimpleDocOption("-linksource")
	val NoComment = SimpleDocOption("-nocomment")
	def access(access: Access.Value) = SimpleDocOption("-access:" + access)
	def documentBottom(bottomText: String) = CompoundDocOption("-bottom", bottomText)
	def documentCharset(charset: String) = CompoundDocOption("-charset", charset)
	def documentTitle(title: String) = CompoundDocOption("-doctitle", title)
	def documentFooter(footerText: String) = CompoundDocOption("-footer", footerText)
	def documentHeader(headerText: String) = CompoundDocOption("-header", headerText)
	def stylesheetFile(path: Path) = CompoundDocOption("-stylesheetfile", path.asFile.getAbsolutePath)
	def documentTop(topText: String) = CompoundDocOption("-top", topText)
	def windowTitle(title: String) = CompoundDocOption("-windowtitle", title)
	
	object Access extends Enumeration
	{
		val Public = Value("public")
		val Default = Value("protected")
		val Private = Value("private")
	}

	def javapTask(classpath: PathFinder, conditional: CompileConditional, compilePath: Path) =
	 task { args =>
		val cp = classpath +++ Path.fromFile(FileUtilities.scalaLibraryJar) +++ Path.fromFile(FileUtilities.scalaCompilerJar)
		execOut { Process("javap" :: "-classpath" :: Path.makeString(cp.get) :: args.toList) }
	} completeWith(classNames(conditional, compilePath))
	private def classNames(conditional: CompileConditional, compilePath: Path) =
	{
		val classes = conditional.analysis.allProducts.flatMap(Path.relativize(compilePath, _))
		classes.map(_.relativePath.replace(java.io.File.separatorChar, '.').toList.dropRight(".class".length).mkString).toSeq
	}
	
	def consoleTask(classpath : PathFinder): Task = 
		consoleTask(classpath, Run)
	def consoleTask(classpath : PathFinder, runner: ScalaRun): Task =
		interactiveTask { runner.console(classpath.get, log) }

	def runTask(mainClass: => Option[String], classpath: PathFinder, options: String*): Task =
		runTask(mainClass, classpath, options)
	def runTask(mainClass: => Option[String], classpath: PathFinder, options: => Seq[String]): Task =
		runTask(mainClass, classpath, options, Run)
	def runTask(mainClass: => Option[String], classpath: PathFinder, options: => Seq[String], runner: ScalaRun): Task =
		task
		{
			mainClass match
			{
				case Some(main) => runner.run(main, classpath.get, options, log)
				case None => Some("No main class specified.")
			}
		}
		
	def syncPathsTask(sources: PathFinder, destinationDirectory: Path): Task =
		task { FileUtilities.syncPaths(sources, destinationDirectory, log) }
	def syncTask(sourceDirectory: Path, destinationDirectory: Path): Task =
		task { FileUtilities.sync(sourceDirectory, destinationDirectory, log) }
	def copyTask(sources: PathFinder, destinationDirectory: Path): Task =
		task { FileUtilities.copy(sources.get, destinationDirectory, log).left.toOption }

	def testTask(frameworks: Iterable[TestFramework], classpath: PathFinder, analysis: CompileAnalysis, options: TestOption*): Task =
		testTask(frameworks, classpath, analysis, options)
	def testTask(frameworks: Iterable[TestFramework], classpath: PathFinder, analysis: CompileAnalysis, options: => Seq[TestOption]): Task =
	{
		def work =
		{
			val (begin, work, end) = testTasks(frameworks, classpath, analysis, options)
			val beginTasks = begin.map(toTask).toSeq // test setup tasks
			val workTasks = work.map(w => toTask(w) dependsOn(beginTasks : _*)) // the actual tests
			val endTasks = end.map(toTask).toSeq // tasks that perform test cleanup and are run regardless of success of tests
			val endTask = task { None } named("test-cleanup") dependsOn(endTasks : _*)
			val rootTask = task { None } named("test-complete") dependsOn(workTasks.toSeq : _*) // the task that depends on all test subtasks
			SubWork[Project#Task](rootTask, endTask)
		}
		new CompoundTask(work)
	}
	private def toTask(testTask: NamedTestTask) = task(testTask.run()) named(testTask.name)

	def graphTask(outputDirectory: Path, analysis: CompileAnalysis): Task = task { DotGraph(analysis, outputDirectory, log) }
	def scaladocTask(label: String, sources: PathFinder, outputDirectory: Path, classpath: PathFinder, options: ScaladocOption*): Task =
		scaladocTask(label, sources, outputDirectory, classpath, options)
	def scaladocTask(label: String, sources: PathFinder, outputDirectory: Path, classpath: PathFinder, options: => Seq[ScaladocOption]): Task =
		task
		{
			val classpathString = Path.makeString(classpath.get)
			val optionsLocal = options
			val maxErrors = maximumErrors(optionsLocal)
			(new Scaladoc(maxErrors))(label, sources.get, classpathString, outputDirectory, optionsLocal.flatMap(_.asList), log)
		}

	def packageTask(sources: PathFinder, outputDirectory: Path, jarName: => String, options: PackageOption*): Task =
		packageTask(sources, outputDirectory / jarName, options)
	def packageTask(sources: PathFinder, outputDirectory: Path, jarName: => String, options: => Seq[PackageOption]): Task =
		packageTask(sources: PathFinder, outputDirectory / jarName, options)
	def packageTask(sources: PathFinder, jarPath: => Path, options: PackageOption*): Task =
		packageTask(sources, jarPath, options)
	def packageTask(sources: PathFinder, jarPath: => Path, options: => Seq[PackageOption]): Task =
		fileTask("package", jarPath from sources)
		{
			import wrap.{MutableMapWrapper,Wrappers}
			/** Copies the mappings in a2 to a1, mutating a1. */
			def mergeAttributes(a1: Attributes, a2: Attributes)
			{
				for( (key, value) <- Wrappers.toList(a2))
					a1.put(key, value)
			}

			val manifest = new Manifest
			var recursive = false
			for(option <- options)
			{
				option match
				{
					case JarManifest(mergeManifest) => 
					{
						mergeAttributes(manifest.getMainAttributes, mergeManifest.getMainAttributes)
						val entryMap = new MutableMapWrapper(manifest.getEntries)
						for((key, value) <- Wrappers.toList(mergeManifest.getEntries))
						{
							entryMap.get(key) match
							{
								case Some(attributes) => mergeAttributes(attributes, value)
								case None => entryMap += (key, value)
							}
						}
					}
					case Recursive => recursive = true
					case MainClass(mainClassName) =>
						manifest.getMainAttributes.put(Attributes.Name.MAIN_CLASS, mainClassName)
					case ManifestAttributes(attributes @ _*) =>
						val main = manifest.getMainAttributes
						for( (name, value) <- attributes)
							main.put(name, value)
					case _ => log.warn("Ignored unknown package option " + option)
				}
			}
			val jarPathLocal = jarPath
			FileUtilities.clean(jarPathLocal :: Nil, log) orElse
			FileUtilities.jar(sources.get, jarPathLocal, manifest, recursive, log)
		}
	def zipTask(sources: PathFinder, outputDirectory: Path, zipName: => String): Task =
		zipTask(sources, outputDirectory / zipName)
	def zipTask(sources: PathFinder, zipPath: => Path): Task =
		fileTask("zip", zipPath from sources) { FileUtilities.zip(sources.get, zipPath, false, log) }
	def incrementVersionNumber()
	{
		projectVersion.get match
		{
			case Some(v: BasicVersion) =>
			{
				val newVersion = incrementImpl(v)
				log.info("Changing version to " + newVersion)
				projectVersion() = newVersion
			}
			case a => ()
		}
	}
	protected def incrementImpl(v: BasicVersion): Version = v.incrementMicro
	protected def testTasks(frameworks: Iterable[TestFramework], classpath: PathFinder, analysis: CompileAnalysis, options: => Seq[TestOption]) = {
		import scala.collection.mutable.HashSet

			val testFilters = new ListBuffer[String => Boolean]
			val excludeTestsSet = new HashSet[String]
			val setup, cleanup = new ListBuffer[() => Option[String]]
			val testListeners = new ListBuffer[TestReportListener]
			
			options.foreach {
				case TestFilter(include) => testFilters += include
				case ExcludeTests(exclude) => excludeTestsSet ++= exclude
				case TestListeners(listeners) => testListeners ++= listeners
				case TestSetup(setupFunction) => setup += setupFunction
				case TestCleanup(cleanupFunction) => cleanup += cleanupFunction
			}
			
			if(excludeTestsSet.size > 0 && log.atLevel(Level.Debug))
			{
				log.debug("Excluding tests: ")
				excludeTestsSet.foreach(test => log.debug("\t" + test))
			}
			def includeTest(test: TestDefinition) = !excludeTestsSet.contains(test.testClassName) && testFilters.forall(filter => filter(test.testClassName))
			val tests = HashSet.empty[TestDefinition] ++ analysis.allTests.filter(includeTest)
			TestFramework.testTasks(frameworks, classpath.get, tests, log, testListeners.readOnly, false, setup.readOnly, cleanup.readOnly)
	}
	private def flatten[T](i: Iterable[Iterable[T]]) = i.flatMap(x => x)
	
	protected def testQuickMethod(testAnalysis: CompileAnalysis, options: => Seq[TestOption])(toRun: Seq[TestOption] => Task) =
		multiTask(testAnalysis.allTests.map(_.testClassName).toList) { includeFunction =>
			toRun(TestFilter(includeFunction) :: options.toList)
		}
		
	protected final def maximumErrors[T <: ActionOption](options: Seq[T]) =
		(for( MaxCompileErrors(maxErrors) <- options) yield maxErrors).firstOption.getOrElse(DefaultMaximumCompileErrors)
}
trait WebScalaProject extends ScalaProject
{
	@deprecated protected def prepareWebappTask(webappContents: PathFinder, warPath: => Path, classpath: PathFinder, extraJars: => Iterable[File]): Task =
		prepareWebappTask(webappContents, warPath, classpath, Path.lazyPathFinder(extraJars.map(Path.fromFile)))
	protected def prepareWebappTask(webappContents: PathFinder, warPath: => Path, classpath: PathFinder, extraJars: PathFinder): Task =
		task
		{
			val webInfPath = warPath / "WEB-INF"
			val webLibDirectory = webInfPath / "lib"
			val classesTargetDirectory = webInfPath / "classes"
			
			val (libs, directories) = classpath.get.toList.partition(ClasspathUtilities.isArchive)
			val classesAndResources = descendents(Path.lazyPathFinder(directories) ##, "*")
			if(log.atLevel(Level.Debug))
				directories.foreach(d => log.debug(" Copying the contents of directory " + d + " to " + classesTargetDirectory))
			
			import FileUtilities.{copy, copyFlat, copyFilesFlat, clean}
			(copy(webappContents.get, warPath, log).right flatMap { copiedWebapp =>
			copy(classesAndResources.get, classesTargetDirectory, log).right flatMap { copiedClasses =>
			copyFlat(libs, webLibDirectory, log).right flatMap { copiedLibs =>
			copyFilesFlat(extraJars.get.map(_.asFile), webLibDirectory, log).right flatMap { copiedExtraLibs =>
				{
					val toRemove = scala.collection.mutable.HashSet((warPath ** "*").get.toSeq : _*)
					toRemove --= copiedWebapp
					toRemove --= copiedClasses
					toRemove --= copiedLibs
					toRemove --= copiedExtraLibs
					val (directories, files) = toRemove.toList.partition(_.isDirectory)
					if(log.atLevel(Level.Debug))
						files.foreach(r => log.debug("Pruning file " + r))
					val result =
						clean(files, true, log) orElse
						{
							val emptyDirectories = directories.filter(directory => directory.asFile.listFiles.isEmpty)
							if(log.atLevel(Level.Debug))
								emptyDirectories.foreach(r => log.debug("Pruning directory " + r))
							clean(emptyDirectories, true, log)
						}
					result.toLeft(())
				}
			}}}}).left.toOption
		}
	def jettyRunTask(jettyRun: JettyRunner) = task { jettyRun() }
	def jettyStopTask(jettyRun: JettyRunner) = task { jettyRun.stop(); None }
}
object ScalaProject
{
	val DefaultMaximumCompileErrors = 100
	val AnalysisDirectoryName = "analysis"
	val MainClassKey = "Main-Class"
	val TestResourcesProperty = "sbt.test.resources"
	def optionsAsString(options: Seq[ScalaProject#CompileOption]) = options.map(_.asString).filter(!_.isEmpty)
	def javaOptionsAsString(options: Seq[ScalaProject#JavaCompileOption]) = options.map(_.asString)
}
trait MultiTaskProject extends Project
{
	def multiTask(allTests: => List[String])(run: (String => Boolean) => Task) =
		task { tests =>
			def filterInclude =
			{
				val (exactFilters, testFilters) = tests.toList.map(GlobFilter.apply).partition(_.isInstanceOf[ExactFilter])
				val includeTests = exactFilters.map(_.asInstanceOf[ExactFilter].matchName)
				val toCheck = scala.collection.mutable.HashSet(includeTests: _*)
				toCheck --= allTests
				if(!toCheck.isEmpty && log.atLevel(Level.Warn))
				{
					log.warn("Test(s) not found:")
					toCheck.foreach(test => log.warn("\t" + test))
				}
				val includeTestsSet = Set(includeTests: _*)
				(test: String) => includeTestsSet.contains(test) || testFilters.exists(_.accept(test))
			}
			val includeFunction =
				if(tests.isEmpty)
					(test: String) => true
				else
					filterInclude
			run(includeFunction)
		} completeWith allTests
	
}
trait ExecProject extends Project
{
	def execOut(p: => ProcessBuilder) =
		task
		{
			val exitValue = (p !)
			if(exitValue == 0)
				None
			else
				Some("Nonzero exit value: " + exitValue)
		}
	def execTask(buildCommand: => ProcessBuilder): Task =
		task
		{
			val command = buildCommand
			log.debug("Executing command " + command)
			val exitValue = command.run(log).exitValue() // don't buffer output
			if(exitValue == 0)
				None
			else
				Some("Nonzero exit value: " + exitValue)
		}
}
trait Exec extends SimpleScalaProject
{
	lazy val sh = task { args =>  execOut { Process("sh" :: "-c" :: args.mkString(" ") :: Nil) } }
	lazy val exec = task { args => execOut { Process(args) } }
}