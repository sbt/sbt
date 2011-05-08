/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

	import java.io.File
	import java.net.URI
	import compiler.{Eval, EvalImports}
	import complete.DefaultParsers.validID
	import Compiler.Compilers
	import Project.{ScopedKey, Setting}
	import Keys.Streams
	import Scope.GlobalScope
	import scala.annotation.tailrec

// name is more like BuildDefinition, but that is too long
trait Build
{
	def projects: Seq[Project]
	def settings: Seq[Setting[_]] = Defaults.buildCore
	def buildResolvers: Seq[BuildLoader.BuildResolver] = Nil
}
trait Plugin
{
	def settings: Seq[Project.Setting[_]] = Nil
}

object Build
{
	def default(base: File): Build = new Build { def projects = defaultProject("default", base) :: Nil }
	def defaultProject(id: String, base: File): Project = Project(id, base)

	def data[T](in: Seq[Attributed[T]]): Seq[T] = in.map(_.data)
	def analyzed(in: Seq[Attributed[_]]): Seq[inc.Analysis] = in.flatMap{ _.metadata.get(Keys.analysis) }
}
object RetrieveUnit
{
	def apply(tempDir: File, base: URI): Option[() => File] =
	{
		lazy val tmp = temporary(tempDir, base)
		base.getScheme match
		{
			case "git" => Some { () => gitClone(base, tmp); tmp }
			case "http" | "https" => Some { () => downloadAndExtract(base, tmp); tmp }
			case "file" => 
				val f = new File(base)
				if(f.isDirectory) Some(() => f) else None
			case _ => None
		}
	}
	def downloadAndExtract(base: URI, tempDir: File): Unit = if(!tempDir.exists) IO.unzipURL(base.toURL, tempDir)
	def temporary(tempDir: File, uri: URI): File = new File(tempDir, Hash.halve(hash(uri)))
	def hash(uri: URI): String = Hash.toHex(Hash(uri.toASCIIString))

	import Process._
	def gitClone(base: URI, tempDir: File): Unit =
		if(!tempDir.exists)  ("git" :: "clone" :: dropFragment(base).toASCIIString :: tempDir.getAbsolutePath :: branch(base)) ! ;
	def branch(base: URI): List[String]  =  base.getFragment match { case null => Nil; case b => "-b" :: b :: Nil }
	def dropFragment(base: URI): URI = if(base.getFragment eq null) base else new URI(base.getScheme, base.getSchemeSpecificPart, null)
}
object EvaluateConfigurations
{
	def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): Seq[Setting[_]] =
		srcs.sortBy(_.getName) flatMap { src =>  evaluateConfiguration(eval, src, imports) }
	def evaluateConfiguration(eval: Eval, src: File, imports: Seq[String]): Seq[Setting[_]] =
		evaluateConfiguration(eval, src.getPath, IO.readLines(src), imports, 0)
	def evaluateConfiguration(eval: Eval, name: String, lines: Seq[String], imports: Seq[String], offset: Int): Seq[Setting[_]] =
	{
		val (importExpressions, settingExpressions) = splitExpressions(lines)
		addOffset(offset, settingExpressions) flatMap { case (settingExpression,line) =>
			evaluateSetting(eval, name, (imports.map(s => (s, -1)) ++ addOffset(offset, importExpressions)), settingExpression, line)
		}
	}
	def addOffset(offset: Int, lines: Seq[(String,Int)]): Seq[(String,Int)] =
		lines.map { case (s, i) => (s, i + offset) }

	def evaluateSetting(eval: Eval, name: String, imports: Seq[(String,Int)], expression: String, line: Int): Seq[Setting[_]] =
	{
		val result = try {
			eval.eval(expression, imports = new EvalImports(imports, name), srcName = name, tpeName = Some("sbt.Project.SettingsDefinition"), line = line)
		} catch {
			case e: sbt.compiler.EvalException => throw new MessageOnlyException(e.getMessage)
		}
		result.value.asInstanceOf[Project.SettingsDefinition].settings
	}
	private[this] def fstS(f: String => Boolean): ((String,Int)) => Boolean = { case (s,i) => f(s.trim) }
	def splitExpressions(lines: Seq[String]): (Seq[(String,Int)], Seq[(String,Int)]) =
	{
		val blank = (_: String).isEmpty
		val comment = (_: String).startsWith("//")
		val blankOrComment = (s: String) => blank(s) || comment(s)
		val importOrBlank = fstS(t => blankOrComment(t) || (t startsWith "import "))

		val (imports, settings) = lines.zipWithIndex span importOrBlank
		(imports filterNot fstS( blankOrComment ), groupedLines(settings, blank, blankOrComment))
	}
	def groupedLines(lines: Seq[(String,Int)], delimiter: String => Boolean, skipInitial: String => Boolean): Seq[(String,Int)] =
	{
		val fdelim = fstS(delimiter)
		@tailrec def group0(lines: Seq[(String,Int)], accum: Seq[(String,Int)]): Seq[(String,Int)] =
			if(lines.isEmpty) accum.reverse
			else
			{
				val start = lines dropWhile fstS( skipInitial )
				val (next, tail) = start.span { case (s,_) => !delimiter(s) }
				val grouped = if(next.isEmpty) accum else (next.map(_._1).mkString("\n"), next.head._2) +: accum
				group0(tail, grouped)
			}
		group0(lines, Nil)
	}
}
object Index
{
	def taskToKeyMap(data: Settings[Scope]): Map[Task[_], ScopedKey[Task[_]]] =
	{
		// AttributeEntry + the checked type test 'value: Task[_]' ensures that the cast is correct.
		//  (scalac couldn't determine that 'key' is of type AttributeKey[Task[_]] on its own and a type match still required the cast)
		val pairs = for( scope <- data.scopes; AttributeEntry(key, value: Task[_]) <- data.data(scope).entries ) yield
			(value, ScopedKey(scope, key.asInstanceOf[AttributeKey[Task[_]]])) // unclear why this cast is needed even with a type test in the above filter
		pairs.toMap[Task[_], ScopedKey[Task[_]]]
	}
	def stringToKeyMap(settings: Settings[Scope]): Map[String, AttributeKey[_]] =
	{
		val multiMap = settings.data.values.flatMap(_.keys).toList.distinct.groupBy(_.label)
		val duplicates = multiMap collect { case (k, x1 :: x2 :: _) => k }
		if(duplicates.isEmpty)
			multiMap.collect { case (k, v) if validID(k) => (k, v.head) } toMap;
		else
			error(duplicates.mkString("AttributeKey ID collisions detected for '", "', '", "'"))
	}
	private[this] type TriggerMap = collection.mutable.HashMap[Task[_], Seq[Task[_]]]
	def triggers(ss: Settings[Scope]): Triggers[Task] =
	{
		val runBefore = new TriggerMap
		val triggeredBy = new TriggerMap
		for( (_, amap) <- ss.data; AttributeEntry(_, value: Task[_]) <- amap.entries)
		{
			val as = value.info.attributes
			update(runBefore, value, as get Keys.runBefore)
			update(triggeredBy, value, as get Keys.triggeredBy)
		}
		new Triggers[Task](runBefore, triggeredBy)
	}
	private[this] def update(map: TriggerMap, base: Task[_], tasksOpt: Option[Seq[Task[_]]]): Unit =
		for( tasks <- tasksOpt; task <- tasks )
			map(task) = base +: map.getOrElse(task, Nil)
}
object BuildStreams
{
		import Load.{BuildStructure, LoadedBuildUnit}
		import Project.display
		import std.{TaskExtra,Transform}
		import Path._
		import BuildPaths.outputDirectory
	
	final val GlobalPath = "$global"
	final val BuildUnitPath = "$build"
	final val StreamsDirectory = "streams"

	def mkStreams(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope]): Streams =
		std.Streams( path(units, root, data), display, LogManager.construct(data) )
		
	def path(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope])(scoped: ScopedKey[_]): File =
		resolvePath( projectPath(units, root, scoped, data), nonProjectPath(scoped) )

	def resolvePath(base: File, components: Seq[String]): File =
		(base /: components)( (b,p) => new File(b,p) )

	def pathComponent[T](axis: ScopeAxis[T], scoped: ScopedKey[_], label: String)(show: T => String): String =
		axis match
		{
			case Global => GlobalPath
			case This => error("Unresolved This reference for " + label + " in " + display(scoped))
			case Select(t) => show(t)
		}
	def nonProjectPath[T](scoped: ScopedKey[T]): Seq[String] =
	{
		val scope = scoped.scope
		pathComponent(scope.config, scoped, "config")(_.name) ::
		pathComponent(scope.task, scoped, "task")(_.label) ::
		pathComponent(scope.extra, scoped, "extra")(_ => error("Unimplemented")) ::
		Nil
	}
	def projectPath(units: Map[URI, LoadedBuildUnit], root: URI, scoped: ScopedKey[_], data: Settings[Scope]): File =
		scoped.scope.project match
		{
			case Global => refTarget(GlobalScope, units(root).localBase, data) / GlobalPath
			case Select(br @ BuildRef(uri)) => refTarget(br, units(uri).localBase, data) / BuildUnitPath
			case Select(pr @ ProjectRef(uri, id)) => refTarget(pr, units(uri).defined(id).base, data)
			case Select(pr) => error("Unresolved project reference (" + pr + ") in " + display(scoped))
			case This => error("Unresolved project reference (This) in " + display(scoped))
		}
		
	def refTarget(ref: ResolvedReference, fallbackBase: File, data: Settings[Scope]): File =
		refTarget(GlobalScope.copy(project = Select(ref)), fallbackBase, data)
	def refTarget(scope: Scope, fallbackBase: File, data: Settings[Scope]): File =
		(Keys.target in scope get data getOrElse outputDirectory(fallbackBase).asFile ) / StreamsDirectory
}
object BuildPaths
{
	import Path._
	import GlobFilter._

	def defaultStaging = Path.userHome / ConfigDirectoryName / "staging"
	def defaultGlobalPlugins = Path.userHome / ConfigDirectoryName / PluginsDirectoryName
	
	def definitionSources(base: File): Seq[File] = (base * "*.scala").getFiles
	def configurationSources(base: File): Seq[File] = (base * "*.sbt").getFiles
	def pluginDirectory(definitionBase: Path) = definitionBase / PluginsDirectoryName

	def evalOutputDirectory(base: Path) = outputDirectory(base) / "config-classes"
	def outputDirectory(base: Path) = base / DefaultTargetName
	def buildOutputDirectory(base: Path, compilers: Compilers) = crossPath(outputDirectory(base), compilers.scalac.scalaInstance)

	def projectStandard(base: Path) = base / "project"
	def projectHidden(base: Path) = base / ConfigDirectoryName
	def selectProjectDir(base: Path) =
	{
		val a = projectHidden(base)
		val b = projectStandard(base)
		if(a.exists) a else b
	}

	final val PluginsDirectoryName = "plugins"
	final val DefaultTargetName = "target"
	final val ConfigDirectoryName = ".sbt"

	def crossPath(base: File, instance: ScalaInstance): File = base / ("scala_" + instance.version)
}