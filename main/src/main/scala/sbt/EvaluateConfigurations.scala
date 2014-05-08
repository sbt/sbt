/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

import java.io.File
import java.net.URI
import compiler.{ Eval, EvalImports }
import complete.DefaultParsers.validID
import Def.{ ScopedKey, Setting, SettingsDefinition }
import Scope.GlobalScope
import scala.annotation.tailrec

/**
 *  This file is responsible for compiling the .sbt files used to configure sbt builds.
 *
 *  Compilation is done in three phases:
 *
 *  1. Parsing high-level constructs (definitions, settings, imports)
 *  2. Compiling scala code into local .class files
 *  3. Evaluating the expressions and obtaining in-memory objects of the results (Setting[_] instances, or val references).
 *
 *
 */
object EvaluateConfigurations {
  /**
   * This represents the parsed expressions in a build sbt, as well as where they were defined.
   */
  private[this] final class ParsedFile(val imports: Seq[(String, Int)], val definitions: Seq[(String, LineRange)], val settings: Seq[(String, LineRange)])

  /** The keywords we look for when classifying a string as a definition. */
  private[this] val DefinitionKeywords = Seq("lazy val ", "def ", "val ")

  /**
   * Using an evaluating instance of the scala compiler, a sequence of files and
   *  the default imports to use, this method will take a ClassLoader of sbt-classes and
   *  return a parsed, compiled + evaluated [[LoadedSbtFile]].   The result has
   *  raw sbt-types that can be accessed and used.
   */
  @deprecated("We no longer merge build.sbt files together unless they are in the same directory.", "0.13.6")
  def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): ClassLoader => LoadedSbtFile =
    {
      val loadFiles = srcs.sortBy(_.getName) map { src => evaluateSbtFile(eval, src, IO.readLines(src), imports, 0) }
      loader => (LoadedSbtFile.empty /: loadFiles) { (loaded, load) => loaded merge load(loader) }
    }

  /**
   * Reads a given .sbt file and evaluates it into a sequence of setting values.
   *
   * Note: This ignores any non-Setting[_] values in the file.
   */
  def evaluateConfiguration(eval: Eval, src: File, imports: Seq[String]): ClassLoader => Seq[Setting[_]] =
    evaluateConfiguration(eval, src, IO.readLines(src), imports, 0)

  /**
   * Parses a sequence of build.sbt lines into a [[ParsedFile]].  The result contains
   * a fragmentation of all imports, settings and definitions.
   *
   * @param buildinImports  The set of import statements to add to those parsed in the .sbt file.
   */
  private[this] def parseConfiguration(lines: Seq[String], builtinImports: Seq[String], offset: Int): ParsedFile =
    {
      val (importStatements, settingsAndDefinitions) = splitExpressions(lines)
      val allImports = builtinImports.map(s => (s, -1)) ++ addOffset(offset, importStatements)
      val (definitions, settings) = splitSettingsDefinitions(addOffsetToRange(offset, settingsAndDefinitions))
      new ParsedFile(allImports, definitions, settings)
    }

  /**
   * Evaluates a  parsed sbt configuration file.
   *
   * @param eval    The evaluating scala compiler instance we use to handle evaluating scala configuration.
   * @param file    The file we've parsed
   * @param imports The default imports to use in this .sbt configuration
   * @param lines   The lines of the configurtion we'd like to evaluate.
   *
   * @return Just the Setting[_] instances defined in the .sbt file.
   */
  def evaluateConfiguration(eval: Eval, file: File, lines: Seq[String], imports: Seq[String], offset: Int): ClassLoader => Seq[Setting[_]] =
    {
      val l = evaluateSbtFile(eval, file, lines, imports, offset)
      loader => l(loader).settings
    }

  /**
   * Evaluates a parsed sbt configuration file.
   *
   * @param eval    The evaluating scala compiler instance we use to handle evaluating scala configuration.
   * @param file    The file we've parsed
   * @param lines   The lines of the configurtion we'd like to evaluate.
   * @param imports The default imports to use in this .sbt configuration.
   *
   * @return A function which can take an sbt classloader and return the raw types/configuratoin
   *         which was compiled/parsed for the given file.
   */
  private[sbt] def evaluateSbtFile(eval: Eval, file: File, lines: Seq[String], imports: Seq[String], offset: Int): ClassLoader => LoadedSbtFile =
    {
      // TODO - Store the file on the LoadedSbtFile (or the parent dir) so we can accurately do
      //        detection for which project project manipulations should be applied.
      val name = file.getPath
      val parsed = parseConfiguration(lines, imports, offset)
      val (importDefs, projects) = if (parsed.definitions.isEmpty) (Nil, (l: ClassLoader) => Nil) else {
        val definitions = evaluateDefinitions(eval, name, parsed.imports, parsed.definitions)
        val imp = BuildUtil.importAllRoot(definitions.enclosingModule :: Nil)
        val projs = (loader: ClassLoader) => definitions.values(loader).map(p => resolveBase(file.getParentFile, p.asInstanceOf[Project]))
        (imp, projs)
      }
      val allImports = importDefs.map(s => (s, -1)) ++ parsed.imports
      val dslEntries = parsed.settings map {
        case (dslExpression, range) =>
          evaluateDslEntry(eval, name, allImports, dslExpression, range)
      }
      eval.unlinkDeferred()
      loader => {
        val (settingsRaw, manipulationsRaw) =
          dslEntries map (_ apply loader) partition {
            case internals.ProjectSettings(_) => true
            case _                            => false
          }
        val settings = settingsRaw flatMap {
          case internals.ProjectSettings(settings) => settings
          case _                                   => Nil
        }
        val manipulations = manipulationsRaw map {
          case internals.ProjectManipulation(f) => f
        }
        val ps = projects(loader)
        // TODO -get project manipulations.
        new LoadedSbtFile(settings, ps, importDefs, manipulations)
      }
    }
  /** move a project to be relative to this file after we've evaluated it. */
  private[this] def resolveBase(f: File, p: Project) = p.copy(base = IO.resolve(f, p.base))
  @deprecated("Will no longer be public.", "0.13.6")
  def flatten(mksettings: Seq[ClassLoader => Seq[Setting[_]]]): ClassLoader => Seq[Setting[_]] =
    loader => mksettings.flatMap(_ apply loader)
  def addOffset(offset: Int, lines: Seq[(String, Int)]): Seq[(String, Int)] =
    lines.map { case (s, i) => (s, i + offset) }
  def addOffsetToRange(offset: Int, ranges: Seq[(String, LineRange)]): Seq[(String, LineRange)] =
    ranges.map { case (s, r) => (s, r shift offset) }

  /**
   * The name of the class we cast DSL "setting" (vs. definition) lines to.
   */
  val SettingsDefinitionName = {
    val _ = classOf[sbt.internals.DslEntry] // this line exists to try to provide a compile-time error when the following line needs to be changed
    "sbt.internals.DslEntry"
  }

  /**
   * This actually compiles a scala expression which represents a sbt.internals.DslEntry.
   *
   * @param eval The mechanism to compile and evaluate Scala expressions.
   * @param name The name for the thing we're compiling
   * @param imports The scala imports to have in place when we compile the expression
   * @param expression The scala expression we're compiling
   * @param range The original position in source of the expression, for error messages.
   *
   * @return A method that given an sbt classloader, can return the actual [[DslEntry]] defined by
   *         the expression.
   */
  private[sbt] def evaluateDslEntry(eval: Eval, name: String, imports: Seq[(String, Int)], expression: String, range: LineRange): ClassLoader => internals.DslEntry = {
    val result = try {
      eval.eval(expression, imports = new EvalImports(imports, name), srcName = name, tpeName = Some(SettingsDefinitionName), line = range.start)
    } catch {
      case e: sbt.compiler.EvalException => throw new MessageOnlyException(e.getMessage)
    }
    loader => {
      val pos = RangePosition(name, range shift 1)
      result.getValue(loader).asInstanceOf[internals.DslEntry].withPos(pos)
    }
  }

  /**
   * This actually compiles a scala expression which represents a Seq[Setting[_]], although the
   * expression may be just a single setting.
   *
   * @param eval The mechanism to compile and evaluate Scala expressions.
   * @param name The name for the thing we're compiling
   * @param imports The scala imports to have in place when we compile the expression
   * @param expression The scala expression we're compiling
   * @param range The original position in source of the expression, for error messages.
   *
   * @return A method that given an sbt classloader, can return the actual Seq[Setting[_]] defined by
   *         the expression.
   */
  @deprecated("Build DSL now includes non-Setting[_] type settings.", "0.13.6")
  def evaluateSetting(eval: Eval, name: String, imports: Seq[(String, Int)], expression: String, range: LineRange): ClassLoader => Seq[Setting[_]] =
    {
      evaluateDslEntry(eval, name, imports, expression, range) andThen {
        case internals.ProjectSettings(values) => values
        case _                                 => Nil
      }
    }
  private[this] def isSpace = (c: Char) => Character isWhitespace c
  private[this] def fstS(f: String => Boolean): ((String, Int)) => Boolean = { case (s, i) => f(s) }
  private[this] def firstNonSpaceIs(lit: String) = (_: String).view.dropWhile(isSpace).startsWith(lit)
  private[this] def or[A](a: A => Boolean, b: A => Boolean): A => Boolean = in => a(in) || b(in)
  /**
   * Splits a set of lines into (imports, expressions).  That is,
   * anything on the right of the tuple is a scala expression (definition or setting).
   */
  def splitExpressions(lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    {
      val blank = (_: String).forall(isSpace)
      val isImport = firstNonSpaceIs("import ")
      val comment = firstNonSpaceIs("//")
      val blankOrComment = or(blank, comment)
      val importOrBlank = fstS(or(blankOrComment, isImport))

      val (imports, settings) = lines.zipWithIndex span importOrBlank
      (imports filterNot fstS(blankOrComment), groupedLines(settings, blank, blankOrComment))
    }
  def groupedLines(lines: Seq[(String, Int)], delimiter: String => Boolean, skipInitial: String => Boolean): Seq[(String, LineRange)] =
    {
      val fdelim = fstS(delimiter)
      @tailrec def group0(lines: Seq[(String, Int)], accum: Seq[(String, LineRange)]): Seq[(String, LineRange)] =
        if (lines.isEmpty) accum.reverse
        else {
          val start = lines dropWhile fstS(skipInitial)
          val (next, tail) = start.span { case (s, _) => !delimiter(s) }
          val grouped = if (next.isEmpty) accum else (next.map(_._1).mkString("\n"), LineRange(next.head._2, next.last._2 + 1)) +: accum
          group0(tail, grouped)
        }
      group0(lines, Nil)
    }
  private[this] def splitSettingsDefinitions(lines: Seq[(String, LineRange)]): (Seq[(String, LineRange)], Seq[(String, LineRange)]) =
    lines partition { case (line, range) => isDefinition(line) }
  private[this] def isDefinition(line: String): Boolean =
    {
      val trimmed = line.trim
      DefinitionKeywords.exists(trimmed startsWith _)
    }
  private[this] def evaluateDefinitions(eval: Eval, name: String, imports: Seq[(String, Int)], definitions: Seq[(String, LineRange)]) =
    {
      val convertedRanges = definitions.map { case (s, r) => (s, r.start to r.end) }
      val findTypes = (classOf[Project] :: /*classOf[Build] :: */ Nil).map(_.getName)
      eval.evalDefinitions(convertedRanges, new EvalImports(imports, name), name, findTypes)
    }
}
object Index {
  def taskToKeyMap(data: Settings[Scope]): Map[Task[_], ScopedKey[Task[_]]] =
    {
      // AttributeEntry + the checked type test 'value: Task[_]' ensures that the cast is correct.
      //  (scalac couldn't determine that 'key' is of type AttributeKey[Task[_]] on its own and a type match still required the cast)
      val pairs = for (scope <- data.scopes; AttributeEntry(key, value: Task[_]) <- data.data(scope).entries) yield (value, ScopedKey(scope, key.asInstanceOf[AttributeKey[Task[_]]])) // unclear why this cast is needed even with a type test in the above filter
      pairs.toMap[Task[_], ScopedKey[Task[_]]]
    }
  def allKeys(settings: Seq[Setting[_]]): Set[ScopedKey[_]] =
    settings.flatMap(s => if (s.key.key.isLocal) Nil else s.key +: s.dependencies).filter(!_.key.isLocal).toSet
  def attributeKeys(settings: Settings[Scope]): Set[AttributeKey[_]] =
    settings.data.values.flatMap(_.keys).toSet[AttributeKey[_]]
  def stringToKeyMap(settings: Set[AttributeKey[_]]): Map[String, AttributeKey[_]] =
    stringToKeyMap0(settings)(_.rawLabel) ++ stringToKeyMap0(settings)(_.label)

  private[this] def stringToKeyMap0(settings: Set[AttributeKey[_]])(label: AttributeKey[_] => String): Map[String, AttributeKey[_]] =
    {
      val multiMap = settings.groupBy(label)
      val duplicates = multiMap collect { case (k, xs) if xs.size > 1 => (k, xs.map(_.manifest)) } collect { case (k, xs) if xs.size > 1 => (k, xs) }
      if (duplicates.isEmpty)
        multiMap.collect { case (k, v) if validID(k) => (k, v.head) } toMap;
      else
        sys.error(duplicates map { case (k, tps) => "'" + k + "' (" + tps.mkString(", ") + ")" } mkString ("Some keys were defined with the same name but different types: ", ", ", ""))
    }
  private[this]type TriggerMap = collection.mutable.HashMap[Task[_], Seq[Task[_]]]
  def triggers(ss: Settings[Scope]): Triggers[Task] =
    {
      val runBefore = new TriggerMap
      val triggeredBy = new TriggerMap
      for ((_, amap) <- ss.data; AttributeEntry(_, value: Task[_]) <- amap.entries) {
        val as = value.info.attributes
        update(runBefore, value, as get Keys.runBefore)
        update(triggeredBy, value, as get Keys.triggeredBy)
      }
      val onComplete = Keys.onComplete in GlobalScope get ss getOrElse { () => () }
      new Triggers[Task](runBefore, triggeredBy, map => { onComplete(); map })
    }
  private[this] def update(map: TriggerMap, base: Task[_], tasksOpt: Option[Seq[Task[_]]]): Unit =
    for (tasks <- tasksOpt; task <- tasks)
      map(task) = base +: map.getOrElse(task, Nil)
}