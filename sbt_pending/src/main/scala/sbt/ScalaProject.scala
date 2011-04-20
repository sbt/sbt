/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah, David MacIver, Josh Cough
 */
package sbt

import FileUtilities._
import java.io.File
import java.util.jar.{Attributes, Manifest}
import scala.collection.mutable.ListBuffer

	def javapTask(classpath: PathFinder, conditional: => CompileConditional, compilePath: Path) =
	 task { args =>
		val cp = classpath +++ Path.fromFile(FileUtilities.scalaLibraryJar) +++ Path.fromFile(FileUtilities.scalaCompilerJar)
		execOut { Process("javap" :: "-classpath" :: Path.makeString(cp.get) :: args.toList) }
	} completeWith(classNames(conditional, compilePath))
	private def classNames(conditional: CompileConditional, compilePath: Path) =
	{
		val classes = conditional.analysis.allProducts.flatMap(path => Path.relativize(compilePath.asFile, path.asFile))
		classes.map(_.replace(java.io.File.separatorChar, '.').toList.dropRight(".class".length).mkString).toSeq
	}

	def syncPathsTask(sources: PathFinder, destinationDirectory: Path): Task =
		task { FileUtilities.syncPaths(sources, destinationDirectory, log) }
	def syncTask(sourceDirectory: Path, destinationDirectory: Path): Task =
		task { FileUtilities.sync(sourceDirectory, destinationDirectory, log) }
	def copyTask(sources: PathFinder, destinationDirectory: Path): Task =
		task { FileUtilities.copy(sources.get, destinationDirectory, log).left.toOption }

	def graphSourcesTask(outputDirectory: Path, roots: PathFinder, analysis: => CompileAnalysis): Task =
		task { DotGraph.sources(analysis, outputDirectory, roots.get, log) }
	def graphPackagesTask(outputDirectory: Path, roots: PathFinder, analysis: => CompileAnalysis): Task =
		task { DotGraph.packages(analysis, outputDirectory, roots.get, log) }

	def zipTask(sources: PathFinder, outputDirectory: Path, zipName: => String): Task =
		zipTask(sources, outputDirectory / zipName)
	def zipTask(sources: PathFinder, zipPath: => Path): Task =
		fileTask("zip", zipPath from sources) { FileUtilities.zip(sources.get, zipPath, false, log) }
	def incrementVersionNumber()

	protected def testQuickMethod(testAnalysis: CompileAnalysis, options: => Seq[TestOption])(toRun: (Seq[TestOption]) => Task) = {
		def analysis = Set() ++ testAnalysis.allTests.map(_.className)
		multiTask(analysis.toList) { (args, includeFunction) =>
			  toRun(TestArgument(args:_*) :: TestFilter(includeFunction) :: options.toList)
		}
  }
}

trait WebScalaProject extends ScalaProject
{
	protected def packageWarAction(stagedWarPath: Path, ignore: PathFinder, outputWarPath: => Path, options: => Seq[PackageOption]): Task =
		packageTask(descendents(stagedWarPath ###, "*") --- ignore, outputWarPath, options)

	@deprecated protected def prepareWebappTask(webappContents: PathFinder, warPath: => Path, classpath: PathFinder, extraJars: => Iterable[File]): Task =
		prepareWebappTask(webappContents, warPath, classpath, Path.finder(extraJars))
	protected def prepareWebappTask(webappContents: PathFinder, warPath: => Path, classpath: PathFinder, extraJars: PathFinder): Task =
		prepareWebappTask(webappContents, warPath, classpath, extraJars, Path.emptyPathFinder)
	protected def prepareWebappTask(webappContents: PathFinder, warPath: => Path, classpath: PathFinder, extraJars: PathFinder, ignore: PathFinder): Task =
		task
		{
			val webInfPath = warPath / "WEB-INF"
			val webLibDirectory = webInfPath / "lib"
			val classesTargetDirectory = webInfPath / "classes"

			val (libs, directories) = classpath.get.toList.partition(ClasspathUtilities.isArchive)
			val classesAndResources = descendents(Path.lazyPathFinder(directories) ###, "*")
			if(log.atLevel(Level.Debug))
				directories.foreach(d => log.debug(" Copying the contents of directory " + d + " to " + classesTargetDirectory))

			import FileUtilities.{copy, copyFlat, copyFilesFlat, clean}
			(copy(webappContents.get, warPath, log).right flatMap { copiedWebapp =>
			copy(classesAndResources.get, classesTargetDirectory, log).right flatMap { copiedClasses =>
			copyFlat(libs, webLibDirectory, log).right flatMap { copiedLibs =>
			copyFilesFlat(extraJars.getFiles, webLibDirectory, log).right flatMap { copiedExtraLibs =>
				{
					val toRemove = scala.collection.mutable.HashSet(((warPath ** "*") --- ignore).get.toSeq : _*)
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
final class TestSetupException(msg: String) extends RuntimeException(msg)
trait MultiTaskProject extends Project
{
	def multiTask(allTests: => List[String])(run: (Seq[String], String => Boolean) => Task): MethodTask = {

		task { tests =>

			val (testNames, separatorAndArgs) = tests.toList.span(! _.startsWith("--"))
			val testArgs = separatorAndArgs.drop(1)

			def filterInclude =
			{
				lazy val (exactFilters, testFilters) = testNames.toList.map(GlobFilter.apply).partition(_.isInstanceOf[ExactFilter])
				lazy val includeTests = exactFilters.map(_.asInstanceOf[ExactFilter].matchName)
				def checkExistence() =
				{
					val toCheck = Set() ++ includeTests -- allTests

					if(!toCheck.isEmpty)
					{
						log.error("Test(s) not found:")
						toCheck.foreach(test => log.error("\t" + test))
						throw new TestSetupException("Invalid test name(s): " + toCheck.mkString(", "))
					}
				}
				lazy val includeTestsSet =
				{
					checkExistence()
					Set(includeTests: _*)
				}
				(test: String) => includeTestsSet.contains(test) || testFilters.exists(_.accept(test))
			}
			
			val includeFunction =
				if(testNames.isEmpty)
					(test: String) => true
				else
					filterInclude
			run(testArgs, includeFunction)
		} completeWith allTests
  }
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
