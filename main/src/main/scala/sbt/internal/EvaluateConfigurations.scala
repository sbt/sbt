/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{ complete, AttributeEntry, AttributeKey, LineRange, MessageOnlyException, RangePosition, Settings }

import java.io.File
import compiler.{ Eval, EvalImports }
import complete.DefaultParsers.validID
import Def.{ ScopedKey, Setting }
import Scope.GlobalScope
import sbt.internal.parser.SbtParser

import sbt.io.IO

/**
 *  This file is responsible for compiling the .sbt files used to configure sbt builds.
 *
 *  Compilation is done in three phases:
 *
 *  1. Parsing high-level constructs (definitions, settings, imports)
 *  2. Compiling scala code into local .class files
 *  3. Evaluating the expressions and obtaining in-memory objects of the results (Setting[_] instances, or val references).
 */
private[sbt] object EvaluateConfigurations {

  type LazyClassLoaded[T] = ClassLoader => T

  private[sbt] case class TrackedEvalResult[T](generated: Seq[File], result: LazyClassLoaded[T])

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
  def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): LazyClassLoaded[LoadedSbtFile] =
    {
      val loadFiles = srcs.sortBy(_.getName) map { src => evaluateSbtFile(eval, src, IO.readLines(src), imports, 0) }
      loader => (LoadedSbtFile.empty /: loadFiles) { (loaded, load) => loaded merge load(loader) }
    }

  /**
   * Reads a given .sbt file and evaluates it into a sequence of setting values.
   *
   * Note: This ignores any non-Setting[_] values in the file.
   */
  def evaluateConfiguration(eval: Eval, src: File, imports: Seq[String]): LazyClassLoaded[Seq[Setting[_]]] =
    evaluateConfiguration(eval, src, IO.readLines(src), imports, 0)

  /**
   * Parses a sequence of build.sbt lines into a [[ParsedFile]].  The result contains
   * a fragmentation of all imports, settings and definitions.
   *
   * @param builtinImports  The set of import statements to add to those parsed in the .sbt file.
   */
  private[this] def parseConfiguration(file: File, lines: Seq[String], builtinImports: Seq[String], offset: Int): ParsedFile =
    {
      val (importStatements, settingsAndDefinitions) = splitExpressions(file, lines)
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
   * @param lines   The lines of the configuration we'd like to evaluate.
   *
   * @return Just the Setting[_] instances defined in the .sbt file.
   */
  def evaluateConfiguration(eval: Eval, file: File, lines: Seq[String], imports: Seq[String], offset: Int): LazyClassLoaded[Seq[Setting[_]]] =
    {
      val l = evaluateSbtFile(eval, file, lines, imports, offset)
      loader => l(loader).settings
    }

  /**
   * Evaluates a parsed sbt configuration file.
   *
   * @param eval    The evaluating scala compiler instance we use to handle evaluating scala configuration.
   * @param file    The file we've parsed
   * @param imports The default imports to use in this .sbt configuration.
   *
   * @return A function which can take an sbt classloader and return the raw types/configuration
   *         which was compiled/parsed for the given file.
   */
  private[sbt] def evaluateSbtFile(eval: Eval, file: File, lines: Seq[String], imports: Seq[String], offset: Int): LazyClassLoaded[LoadedSbtFile] =
    {
      // TODO - Store the file on the LoadedSbtFile (or the parent dir) so we can accurately do
      //        detection for which project project manipulations should be applied.
      val name = file.getPath
      val parsed = parseConfiguration(file, lines, imports, offset)
      val (importDefs, definitions) =
        if (parsed.definitions.isEmpty) (Nil, DefinedSbtValues.empty) else {
          val definitions = evaluateDefinitions(eval, name, parsed.imports, parsed.definitions, Some(file))
          val imp = BuildUtil.importAllRoot(definitions.enclosingModule :: Nil)
          (imp, DefinedSbtValues(definitions))
        }
      val allImports = importDefs.map(s => (s, -1)) ++ parsed.imports
      val dslEntries = parsed.settings map {
        case (dslExpression, range) =>
          evaluateDslEntry(eval, name, allImports, dslExpression, range)
      }
      eval.unlinkDeferred()
      // Tracks all the files we generated from evaluating the sbt file.
      val allGeneratedFiles = (definitions.generated ++ dslEntries.flatMap(_.generated))
      loader => {
        val projects =
          definitions.values(loader).collect {
            case p: Project => resolveBase(file.getParentFile, p)
          }
        val (settingsRaw, manipulationsRaw) =
          dslEntries map (_.result apply loader) partition {
            case DslEntry.ProjectSettings(_) => true
            case _                           => false
          }
        val settings = settingsRaw flatMap {
          case DslEntry.ProjectSettings(settings) => settings
          case _                                  => Nil
        }
        val manipulations = manipulationsRaw map {
          case DslEntry.ProjectManipulation(f) => f
        }
        // TODO -get project manipulations.
        new LoadedSbtFile(settings, projects, importDefs, manipulations, definitions, allGeneratedFiles)
      }
    }

  /** move a project to be relative to this file after we've evaluated it. */
  private[this] def resolveBase(f: File, p: Project) = p.copy(base = IO.resolve(f, p.base))

  def addOffset(offset: Int, lines: Seq[(String, Int)]): Seq[(String, Int)] =
    lines.map { case (s, i) => (s, i + offset) }

  def addOffsetToRange(offset: Int, ranges: Seq[(String, LineRange)]): Seq[(String, LineRange)] =
    ranges.map { case (s, r) => (s, r shift offset) }

  /**
   * The name of the class we cast DSL "setting" (vs. definition) lines to.
   */
  val SettingsDefinitionName = {
    val _ = classOf[DslEntry] // this line exists to try to provide a compile-time error when the following line needs to be changed
    "sbt.internal.DslEntry"
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
   * @return A method that given an sbt classloader, can return the actual [[sbt.internal.DslEntry]] defined by
   *         the expression, and the sequence of .class files generated.
   */
  private[sbt] def evaluateDslEntry(eval: Eval, name: String, imports: Seq[(String, Int)], expression: String, range: LineRange): TrackedEvalResult[DslEntry] = {
    // TODO - Should we try to namespace these between.sbt files?  IF they hash to the same value, they may actually be
    // exactly the same setting, so perhaps we don't care?
    val result = try {
      eval.eval(expression, imports = new EvalImports(imports, name), srcName = name, tpeName = Some(SettingsDefinitionName), line = range.start)
    } catch {
      case e: sbt.compiler.EvalException => throw new MessageOnlyException(e.getMessage)
    }
    // TODO - keep track of configuration classes defined.
    TrackedEvalResult(
      result.generated,
      loader => {
        val pos = RangePosition(name, range shift 1)
        result.getValue(loader).asInstanceOf[DslEntry].withPos(pos)
      }
    )
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
  // Build DSL now includes non-Setting[_] type settings.
  // Note: This method is used by the SET command, so we may want to evaluate that sucker a bit.
  def evaluateSetting(eval: Eval, name: String, imports: Seq[(String, Int)], expression: String, range: LineRange): LazyClassLoaded[Seq[Setting[_]]] =
    evaluateDslEntry(eval, name, imports, expression, range).result andThen {
      case DslEntry.ProjectSettings(values) => values
      case _                                => Nil
    }

  /**
   * Splits a set of lines into (imports, expressions).  That is,
   * anything on the right of the tuple is a scala expression (definition or setting).
   */
  private[sbt] def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    {
      val split = SbtParser(file, lines)
      // TODO - Look at pulling the parsed expression trees from the SbtParser and stitch them back into a different
      // scala compiler rather than re-parsing.
      (split.imports, split.settings)
    }

  private[this] def splitSettingsDefinitions(lines: Seq[(String, LineRange)]): (Seq[(String, LineRange)], Seq[(String, LineRange)]) =
    lines partition { case (line, range) => isDefinition(line) }

  private[this] def isDefinition(line: String): Boolean =
    {
      val trimmed = line.trim
      DefinitionKeywords.exists(trimmed startsWith _)
    }

  private[this] def extractedValTypes: Seq[String] =
    Seq(classOf[Project], classOf[InputKey[_]], classOf[TaskKey[_]], classOf[SettingKey[_]]).map(_.getName)

  private[this] def evaluateDefinitions(eval: Eval, name: String, imports: Seq[(String, Int)], definitions: Seq[(String, LineRange)], file: Option[File]): compiler.EvalDefinitions =
    {
      val convertedRanges = definitions.map { case (s, r) => (s, r.start to r.end) }
      eval.evalDefinitions(convertedRanges, new EvalImports(imports, name), name, file, extractedValTypes)
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
    stringToKeyMap0(settings)(_.label)

  private[this] def stringToKeyMap0(settings: Set[AttributeKey[_]])(label: AttributeKey[_] => String): Map[String, AttributeKey[_]] =
    {
      val multiMap = settings.groupBy(label)
      val duplicates = multiMap collect { case (k, xs) if xs.size > 1 => (k, xs.map(_.manifest)) } collect { case (k, xs) if xs.size > 1 => (k, xs) }
      if (duplicates.isEmpty)
        multiMap.collect { case (k, v) if validID(k) => (k, v.head) } toMap
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
