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
	import Keys.{globalBaseDirectory, Streams}
	import Scope.GlobalScope
	import scala.annotation.tailrec

// name is more like BuildDefinition, but that is too long
trait Build
{
	def projectDefinitions(baseDirectory: File): Seq[Project] = projects
	def projects: Seq[Project] = ReflectUtilities.allVals[Project](this).values.toSeq
	def settings: Seq[Setting[_]] = Defaults.buildCore
	def buildResolvers: Seq[BuildLoader.BuildResolver] = Nil
}
trait Plugin
{
	def settings: Seq[Project.Setting[_]] = Nil
}

object Build
{
	val default: Build = new Build { override def projectDefinitions(base: File) = defaultProject(base) :: Nil }
	def defaultID(base: File): String = "default-" + Hash.trimHashString(base.getAbsolutePath, 6)
	def defaultProject(base: File): Project = Project(defaultID(base), base).settings(Keys.organization := "default")

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
			case "git" => Some { () => gitRetrieve(base, tmp); tmp }
			case "http" | "https" => Some { () => downloadAndExtract(base, tmp); tmp }
			case "file" => 
				val f = new File(base)
				if(f.isDirectory)
				{
					val finalDir = if (!f.canWrite) retrieveRODir(f, tmp) else f
					Some(() => finalDir)
				}
				else None
			case _ => None
		}
	}
	def retrieveRODir(base: File, tempDir: File): File =
	{
		if (!tempDir.exists)
		{
			try {
				IO.copyDirectory(base, tempDir)
			} catch {
				case e =>
					IO.delete(tempDir)
					throw e
			}
		}
		tempDir
	}
	def downloadAndExtract(base: URI, tempDir: File): Unit = if(!tempDir.exists) IO.unzipURL(base.toURL, tempDir)
	def temporary(tempDir: File, uri: URI): File = new File(tempDir, Hash.halve(hash(uri)))
	def hash(uri: URI): String = Hash.toHex(Hash(uri.toASCIIString))

	import Process._
	def gitRetrieve(base: URI, tempDir: File): Unit =
		if(!tempDir.exists)
		{
			try {
				IO.createDirectory(tempDir)
				gitClone(dropFragment(base), tempDir)
				Option(base.getFragment) foreach { branch => gitCheckout(tempDir, branch) }
			} catch {
				case e => IO.delete(tempDir)
				throw e
			}
		}
	def dropFragment(base: URI): URI = if(base.getFragment eq null) base else new URI(base.getScheme, base.getSchemeSpecificPart, null)
	def gitClone(base: URI, tempDir: File): Unit =
		run("git" :: "clone" :: dropFragment(base).toASCIIString :: tempDir.getAbsolutePath :: Nil, tempDir) ;
	def gitCheckout(tempDir: File, branch: String): Unit =
		run("git" :: "checkout" :: "-q" :: branch :: Nil, tempDir) ;
	def run(command: List[String], cwd: File): Unit =
	{
		val result = Process(command, cwd) ! ;
		if(result != 0)
			error("Nonzero exit code (" + result + "): " + command.mkString(" "))
	}
}
object EvaluateConfigurations
{
	def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): ClassLoader => Seq[Setting[_]] =
		flatten(srcs.sortBy(_.getName) map { src =>  evaluateConfiguration(eval, src, imports) })
	def evaluateConfiguration(eval: Eval, src: File, imports: Seq[String]): ClassLoader => Seq[Setting[_]] =
		evaluateConfiguration(eval, src.getPath, IO.readLines(src), imports, 0)
	def evaluateConfiguration(eval: Eval, name: String, lines: Seq[String], imports: Seq[String], offset: Int): ClassLoader => Seq[Setting[_]] =
	{
		val (importExpressions, settingExpressions) = splitExpressions(lines)
		val settings = addOffset(offset, settingExpressions) map { case (settingExpression,line) =>
			evaluateSetting(eval, name, (imports.map(s => (s, -1)) ++ addOffset(offset, importExpressions)), settingExpression, line)
		}
		flatten(settings)
	}
	def flatten(mksettings: Seq[ClassLoader => Seq[Setting[_]]]): ClassLoader => Seq[Setting[_]] =
		loader => mksettings.flatMap(_ apply loader)
	def addOffset(offset: Int, lines: Seq[(String,Int)]): Seq[(String,Int)] =
		lines.map { case (s, i) => (s, i + offset) }

	def evaluateSetting(eval: Eval, name: String, imports: Seq[(String,Int)], expression: String, line: Int): ClassLoader => Seq[Setting[_]] =
	{
		val result = try {
			eval.eval(expression, imports = new EvalImports(imports, name), srcName = name, tpeName = Some("sbt.Project.SettingsDefinition"), line = line)
		} catch {
			case e: sbt.compiler.EvalException => throw new MessageOnlyException(e.getMessage)
		}
		loader => result.getValue(loader).asInstanceOf[Project.SettingsDefinition].settings
	}
	private[this] def isSpace = (c: Char) => Character isWhitespace c
	private[this] def fstS(f: String => Boolean): ((String,Int)) => Boolean = { case (s,i) => f(s) }
	private[this] def firstNonSpaceIs(lit: String) = (_: String).view.dropWhile(isSpace).startsWith(lit)
	private[this] def or[A](a: A => Boolean, b: A => Boolean): A => Boolean = in => a(in) || b(in)
	def splitExpressions(lines: Seq[String]): (Seq[(String,Int)], Seq[(String,Int)]) =
	{
		val blank = (_: String).forall(isSpace)
		val isImport = firstNonSpaceIs("import ")
		val comment = firstNonSpaceIs("//")
		val blankOrComment = or(blank, comment)
		val importOrBlank = fstS(or(blankOrComment, isImport))

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
	def allKeys(settings: Seq[Setting[_]]): Set[ScopedKey[_]] =
		settings.flatMap(s => s.key +: s.dependsOn).toSet
	def attributeKeys(settings: Settings[Scope]): Set[AttributeKey[_]] =
		settings.data.values.flatMap(_.keys).toSet[AttributeKey[_]]
	def stringToKeyMap(settings: Set[AttributeKey[_]]): Map[String, AttributeKey[_]] =	
	{
		val multiMap = settings.groupBy(_.label)
		val duplicates = multiMap collect { case (k, xs) if xs.size > 1 => (k, xs.map(_.manifest)) } collect { case (k, xs) if xs.size > 1 => (k, xs) }
		if(duplicates.isEmpty)
			multiMap.collect { case (k, v) if validID(k) => (k, v.head) } toMap;
		else
			error(duplicates map { case (k, tps) => "'" + k + "' (" + tps.mkString(", ") + ")" } mkString("AttributeKey ID collisions detected for: ", ", ", ""))
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
		import Project.displayFull
		import std.{TaskExtra,Transform}
		import Path._
		import BuildPaths.outputDirectory
	
	final val GlobalPath = "$global"
	final val BuildUnitPath = "$build"
	final val StreamsDirectory = "streams"

	def mkStreams(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope]): Streams =
		std.Streams( path(units, root, data), displayFull, LogManager.construct(data) )
		
	def path(units: Map[URI, LoadedBuildUnit], root: URI, data: Settings[Scope])(scoped: ScopedKey[_]): File =
		resolvePath( projectPath(units, root, scoped, data), nonProjectPath(scoped) )

	def resolvePath(base: File, components: Seq[String]): File =
		(base /: components)( (b,p) => new File(b,p) )

	def pathComponent[T](axis: ScopeAxis[T], scoped: ScopedKey[_], label: String)(show: T => String): String =
		axis match
		{
			case Global => GlobalPath
			case This => error("Unresolved This reference for " + label + " in " + Project.displayFull(scoped))
			case Select(t) => show(t)
		}
	def nonProjectPath[T](scoped: ScopedKey[T]): Seq[String] =
	{
		val scope = scoped.scope
		pathComponent(scope.config, scoped, "config")(_.name) ::
		pathComponent(scope.task, scoped, "task")(_.label) ::
		pathComponent(scope.extra, scoped, "extra")(showAMap) ::
		Nil
	}
	def showAMap(a: AttributeMap): String =
		a.entries.toSeq.sortBy(_.key.label).map { case AttributeEntry(key, value) => key.label + "=" + value.toString } mkString(" ")
	def projectPath(units: Map[URI, LoadedBuildUnit], root: URI, scoped: ScopedKey[_], data: Settings[Scope]): File =
		scoped.scope.project match
		{
			case Global => refTarget(GlobalScope, units(root).localBase, data) / GlobalPath
			case Select(br @ BuildRef(uri)) => refTarget(br, units(uri).localBase, data) / BuildUnitPath
			case Select(pr @ ProjectRef(uri, id)) => refTarget(pr, units(uri).defined(id).base, data)
			case Select(pr) => error("Unresolved project reference (" + pr + ") in " + displayFull(scoped))
			case This => error("Unresolved project reference (This) in " + displayFull(scoped))
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

	def getGlobalBase(state: State): File  =  state get globalBaseDirectory orElse systemGlobalBase getOrElse defaultGlobalBase
	def systemGlobalBase: Option[File] = Option(System.getProperty(GlobalBaseProperty)) flatMap { path =>
		if(path.isEmpty) None else Some(new File(path))
	}
		
	def defaultGlobalBase = Path.userHome / ConfigDirectoryName
	def defaultStaging(globalBase: File) = globalBase / "staging"
	def defaultGlobalPlugins(globalBase: File) = globalBase / PluginsDirectoryName
	def defaultGlobalSettings(globalBase: File) = configurationSources(globalBase)
	
	def definitionSources(base: File): Seq[File] = (base * "*.scala").get
	def configurationSources(base: File): Seq[File] = (base * "*.sbt").get
	def pluginDirectory(definitionBase: File) = definitionBase / PluginsDirectoryName

	def evalOutputDirectory(base: File) = outputDirectory(base) / "config-classes"
	def outputDirectory(base: File) = base / DefaultTargetName
	def buildOutputDirectory(base: File, compilers: Compilers) = crossPath(outputDirectory(base), compilers.scalac.scalaInstance)

	def projectStandard(base: File) = base / "project"
	def projectHidden(base: File) = base / ConfigDirectoryName
	def selectProjectDir(base: File) =
	{
		val a = projectHidden(base)
		val b = projectStandard(base)
		if(a.exists) a else b
	}

	final val PluginsDirectoryName = "plugins"
	final val DefaultTargetName = "target"
	final val ConfigDirectoryName = ".sbt"
	final val GlobalBaseProperty = "sbt.global.base"

	def crossPath(base: File, instance: ScalaInstance): File = base / ("scala_" + instance.version)
}
