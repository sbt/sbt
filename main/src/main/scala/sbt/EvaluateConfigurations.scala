/* sbt -- Simple Build Tool
 * Copyright 2011 Mark Harrah
 */
package sbt

import java.io.File

import compiler.{ Eval, EvalDefinitions, EvalImports, EvalSbtFile }
import complete.DefaultParsers.validID
import Def.{ ScopedKey, Setting }
import Scope.GlobalScope
import sbt.internals.parser.SbtParser

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

  type LazyClassLoaded[T] = ClassLoader => T

  /**
   * Represents the result of a [[sbt.internals.DslEntry]] evaluation.
   *
   * @param generated The generated class files for the evaluation.
   * @param result A function that will return the loaded value.
   * @tparam T The expected result type of the loaded value.
   */
  private[sbt] case class EvalResult[T](generated: Seq[File], result: LazyClassLoaded[T])

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
  def apply(eval: Eval, srcs: Seq[File], imports: Seq[String]): LazyClassLoaded[LoadedSbtFile] =
    {
      val loadFiles = srcs.sortBy(_.getName) map { src => evaluateSbtFileAtOnce(eval, src, IO.readLines(src), imports, 0) }
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
   * @param lines   The lines of the configurtion we'd like to evaluate.
   *
   * @return Just the Setting[_] instances defined in the .sbt file.
   */
  def evaluateConfiguration(eval: Eval, file: File, lines: Seq[String], imports: Seq[String], offset: Int): LazyClassLoaded[Seq[Setting[_]]] =
    {
      val l = evaluateSbtFileAtOnce(eval, file, lines, imports, offset)
      loader => l(loader).settings
    }

  /**
   * Evaluates a parsed sbt configuration file.
   *
   * @param eval The sbt evaluation logic.
   * @param targetDir The target directory to store class files.
   * @param defaultImports The default imports to use in this .sbt configuration.
   *
   * @return A function which can take an sbt classloader and return the raw types/configuration
   *         which was compiled/parsed for the given file.
   */
  private[sbt] def evaluateSbtFile(
    eval: Eval,
    targetDir: File,
    lines: Seq[String],
    defaultImports: Seq[String],
    offset: Int): LazyClassLoaded[LoadedSbtFile] = {

    // TODO: Detect for which project the project manipulations should be done.
    // For that, store the file on the `LoadedSbtFile` or its parent directory.
    val targetName = targetDir.getPath
    val parsed = parseConfiguration(targetDir, lines, defaultImports, offset)
    val definitions = parsed.definitions
    val imports = parsed.imports
    val (importsForDefinitions, definedSbtValues) = {
      if (definitions.isEmpty) (Nil, DefinedSbtValues.empty)
      else {
        val sbtDefs: EvalDefinitions =
          evaluateDefinitions(eval, targetName, imports, definitions, targetDir)
        val imp = BuildUtil.importAllRoot(sbtDefs.enclosingModule :: Nil)
        (imp, DefinedSbtValues(sbtDefs))
      }
    }

    val allImports = importsForDefinitions.map(s => (s, -1)) ++ imports
    val dslEntries = parsed.settings.map {
      case (dslExpression, range) =>
        evaluateDslEntry(eval, targetName, allImports, dslExpression, range)
    }
    eval.unlinkDeferred()

    val allGeneratedFiles =
      definedSbtValues.generated ++ dslEntries.flatMap(_.generated)

    (loader: ClassLoader) => {
      import internals.{ ProjectSettings, ProjectManipulation }
      val loadedDslEntries = dslEntries.map(_.result.apply(loader))
      val loadedSbtValues = definedSbtValues.values(loader)

      // Get the settings and project manipulations defined by the user
      val empty = (List.empty[Def.Setting[_]], List.empty[Project => Project])
      val (settings, manipulations) = loadedDslEntries.foldLeft(empty) {
        case (previous @ (accSettings, accManipulations), loadedEntry) =>
          loadedEntry match {
            case ProjectSettings(settings0) =>
              (settings0 ++: accSettings, accManipulations)
            case ProjectManipulation(manipulation) =>
              (accSettings, manipulation :: accManipulations)
            case _ => previous
          }
      }

      // Get the defined projects by the user
      val projects = loadedSbtValues.collect {
        case p: Project => resolveBase(targetDir.getParentFile, p)
      }

      new LoadedSbtFile(
        settings.reverse,
        projects,
        importsForDefinitions,
        manipulations.reverse,
        definedSbtValues,
        allGeneratedFiles
      )
    }
  }

  private[sbt] def evaluateSbtFileAtOnce(
    eval: Eval,
    targetDir: File,
    lines: Seq[String],
    defaultImports: Seq[String],
    offset: Int): LazyClassLoaded[LoadedSbtFile] = {

    // TODO: Detect for which project the project manipulations should be done.
    // For that, store the file on the `LoadedSbtFile` or its parent directory.
    val targetName = targetDir.getPath
    val parsed = parseConfiguration(targetDir, lines, defaultImports, offset)
    val definitions = parsed.definitions
    val settings = parsed.settings
    val imports = parsed.imports

    val evaluatedSbtFile: EvalSbtFile =
      loadSbtFile(eval, targetName, imports, definitions, settings, targetDir)
    val allGeneratedFiles: Seq[File] = evaluatedSbtFile.generated
    val fromDefinitionsOwner = List(evaluatedSbtFile.enclosingModule)
    val importForDefinitions = BuildUtil.importAllRoot(fromDefinitionsOwner)
    val definedSbtValues = DefinedSbtValues(evaluatedSbtFile.getEvalDefinitions)

    (loader: ClassLoader) => {
      import internals.{ ProjectSettings, ProjectManipulation }
      val (loadedSbtValues, loadedSettings) =
        evaluatedSbtFile.loadDefinitionsAndSettings(loader)

      // Load settings and assign them positions at sbt file
      val loadedDslEntries = loadedSettings.zip(settings).map {
        case (loadedSetting, (_, range)) =>
          val dslEntry = loadedSetting.asInstanceOf[internals.DslEntry]
          val pos = RangePosition(targetName, range.shift(1))
          dslEntry.withPos(pos)
      }

      // Get the settings and project manipulations defined by the user
      val empty = (List.empty[Def.Setting[_]], List.empty[Project => Project])
      val (settings0, manipulations) = loadedDslEntries.foldLeft(empty) {
        case (previous @ (accSettings, accManipulations), loadedEntry) =>
          loadedEntry match {
            case ProjectSettings(settings1) =>
              (settings1 ++: accSettings, accManipulations)
            case ProjectManipulation(manipulation) =>
              (accSettings, manipulation :: accManipulations)
            case _ => previous
          }
      }

      // Get the defined projects by the user
      val projects = loadedSbtValues.collect {
        case p: Project => resolveBase(targetDir.getParentFile, p)
      }

      new LoadedSbtFile(
        settings0.reverse,
        projects,
        importForDefinitions,
        manipulations.reverse,
        definedSbtValues,
        allGeneratedFiles
      )
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
   * @param fileName The file name of the sbt file that holds this dsl entry. It
   *                 may be a dummy name for evaluations that are written in
   *                 the sbt shell. Several dsl entries can share this name.
   * @param imports The scala imports to have in place when we compile the expression
   * @param expression The scala expression we're compiling
   * @param range The original position in source of the expression, for error messages.
   *
   * @return A method that given an sbt classloader, can return the actual [[internals.DslEntry]] defined by
   *         the expression, and the sequence of .class files generated.
   */
  private[sbt] def evaluateDslEntry(eval: Eval, fileName: String, imports: Seq[(String, Int)], expression: String, range: LineRange): EvalResult[internals.DslEntry] = {
    // TODO - Should we try to namespace these between.sbt files?  IF they hash to the same value, they may actually be
    // exactly the same setting, so perhaps we don't care?
    val result = try {
      eval.eval(expression, imports = new EvalImports(imports, fileName), srcName = fileName, tpeName = Some(SettingsDefinitionName), line = range.start)
    } catch {
      case e: sbt.compiler.EvalException => throw new MessageOnlyException(e.getMessage)
    }
    // TODO - keep track of configuration classes defined.
    val loadDslEntry = {
      (loader: ClassLoader) =>
        val pos = RangePosition(fileName, range.shift(1))
        result.getValue(loader).asInstanceOf[internals.DslEntry].withPos(pos)
    }
    EvalResult(result.generated, loadDslEntry)
  }

  private[this] def loadSbtFile(
    eval: Eval,
    fileName: String,
    imports: Seq[(String, Int)],
    definitions: Seq[(String, LineRange)],
    settings: Seq[(String, LineRange)],
    target: File): compiler.EvalSbtFile = {
    def toRanges(xs: Seq[(String, LineRange)]) =
      xs.map { case (s, r) => (s, r.start to r.end) }

    val positionedDefs = toRanges(definitions)
    val positionedSettings = toRanges(settings)
    val importTrees = new EvalImports(imports, fileName)
    // FIXME: The API should assume type is same for all settings
    val dslType = Some(SettingsDefinitionName)
    val settingsTypes = positionedSettings.map(_ => dslType)

    eval.evalSbtFile(
      positionedDefs,
      positionedSettings,
      settingsTypes,
      importTrees,
      fileName,
      target,
      extractedValTypes
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
  @deprecated("Build DSL now includes non-Setting[_] type settings.", "0.13.6") // Note: This method is used by the SET command, so we may want to evaluate that sucker a bit.
  def evaluateSetting(eval: Eval, name: String, imports: Seq[(String, Int)], expression: String, range: LineRange): LazyClassLoaded[Seq[Setting[_]]] =
    {
      evaluateDslEntry(eval, name, imports, expression, range).result andThen {
        case internals.ProjectSettings(values) => values
        case _                                 => Nil
      }
    }
  private[this] def isSpace = (c: Char) => Character isWhitespace c
  private[this] def fstS(f: String => Boolean): ((String, Int)) => Boolean = { case (s, i) => f(s) }
  private[this] def firstNonSpaceIs(lit: String) = (_: String).view.dropWhile(isSpace).startsWith(lit)
  private[this] def or[A](a: A => Boolean, b: A => Boolean): A => Boolean = in => a(in) || b(in)

  /** Configures the use of the old sbt parser. */
  private[sbt] def useOldParser: Boolean =
    sys.props.get("sbt.parser.simple").exists(java.lang.Boolean.parseBoolean)
  /**
   * Splits a set of lines into (imports, expressions).  That is,
   * anything on the right of the tuple is a scala expression (definition or setting).
   */
  private[sbt] def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    {
      if (useOldParser) splitExpressions(lines)
      else {
        val split = SbtParser(file, lines)
        // TODO - Look at pulling the parsed expression trees from the SbtParser and stitch them back into a different
        // scala compiler rather than re-parsing.
        (split.imports, split.settings)
      }
    }

  @deprecated("This method is no longer part of the public API.", "0.13.7")
  def splitExpressions(lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) = {
    val blank = (_: String).forall(isSpace)
    val isImport = firstNonSpaceIs("import ")
    val comment = firstNonSpaceIs("//")
    val blankOrComment = or(blank, comment)
    val importOrBlank = fstS(or(blankOrComment, isImport))

    val (imports, settings) = lines.zipWithIndex span importOrBlank
    (imports filterNot fstS(blankOrComment), groupedLines(settings, blank, blankOrComment))
  }
  @deprecated("This method is deprecated and no longer used.", "0.13.7")
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
  private[this] def extractedValTypes: Seq[String] =
    Seq(classOf[Project], classOf[InputKey[_]], classOf[TaskKey[_]], classOf[SettingKey[_]]).map(_.getName)
  private[this] def evaluateDefinitions(eval: Eval, name: String, imports: Seq[(String, Int)], definitions: Seq[(String, LineRange)], target: File): compiler.EvalDefinitions =
    {
      val convertedRanges = definitions.map { case (s, r) => (s, r.start to r.end) }
      eval.evalDefinitions(convertedRanges, new EvalImports(imports, name), name, target, extractedValTypes)
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