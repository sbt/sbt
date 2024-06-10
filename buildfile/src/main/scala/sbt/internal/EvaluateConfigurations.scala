/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.internal.util.{
  AttributeEntry,
  AttributeKey,
  LineRange,
  MessageOnlyException,
  RangePosition,
  Settings
}

import java.io.File
import java.nio.file.Path
import sbt.internal.util.complete.DefaultParsers.validID
import Def.{ ScopedKey, Setting }
import Scope.GlobalScope
import sbt.SlashSyntax0.*
import sbt.internal.parser.SbtParser
import sbt.io.IO
import scala.jdk.CollectionConverters.*
import xsbti.PathBasedFile
import xsbti.VirtualFile
import xsbti.VirtualFileRef

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

  type LazyClassLoaded[A] = ClassLoader => A

  private[sbt] case class TrackedEvalResult[A](
      generated: Seq[Path],
      result: LazyClassLoaded[A]
  )

  /**
   * This represents the parsed expressions in a build sbt, as well as where they were defined.
   */
  private[this] final class ParsedFile(
      val imports: Seq[(String, Int)],
      val definitions: Seq[(String, LineRange)],
      val settings: Seq[(String, LineRange)]
  )

  /** The keywords we look for when classifying a string as a definition. */
  private[this] val DefinitionKeywords = Seq("lazy val ", "def ", "val ")

  /**
   * Using an evaluating instance of the scala compiler, a sequence of files and
   *  the default imports to use, this method will take a ClassLoader of sbt-classes and
   *  return a parsed, compiled + evaluated [[LoadedSbtFile]].   The result has
   *  raw sbt-types that can be accessed and used.
   */
  def apply(
      eval: Eval,
      srcs: Seq[VirtualFile],
      imports: Seq[String],
  ): LazyClassLoaded[LoadedSbtFile] = {
    val loadFiles = srcs.sortBy(_.name) map { src =>
      evaluateSbtFile(eval, src, IO.readStream(src.input()).linesIterator.toList, imports, 0)
    }
    loader =>
      loadFiles.foldLeft(LoadedSbtFile.empty) { (loaded, load) =>
        loaded merge load(loader)
      }
  }

  /**
   * Reads a given .sbt file and evaluates it into a sequence of setting values.
   *
   * Note: This ignores any non-Setting[_] values in the file.
   */
  def evaluateConfiguration(
      eval: Eval,
      src: VirtualFile,
      imports: Seq[String]
  ): LazyClassLoaded[Seq[Setting[_]]] =
    evaluateConfiguration(eval, src, IO.readStream(src.input()).linesIterator.toList, imports, 0)

  /**
   * Parses a sequence of build.sbt lines into a [[ParsedFile]].  The result contains
   * a fragmentation of all imports, settings and definitions.
   *
   * @param builtinImports  The set of import statements to add to those parsed in the .sbt file.
   */
  private[this] def parseConfiguration(
      file: VirtualFileRef,
      lines: Seq[String],
      builtinImports: Seq[String],
      offset: Int
  ): ParsedFile = {
    val (importStatements, settingsAndDefinitions) = splitExpressions(file, lines)
    val allImports = builtinImports.map(s => (s, -1)) ++ addOffset(offset, importStatements)
    val (definitions, settings) = splitSettingsDefinitions(
      addOffsetToRange(offset, settingsAndDefinitions)
    )
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
  def evaluateConfiguration(
      eval: Eval,
      file: VirtualFileRef,
      lines: Seq[String],
      imports: Seq[String],
      offset: Int
  ): LazyClassLoaded[Seq[Setting[_]]] = {
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
  private[sbt] def evaluateSbtFile(
      eval: Eval,
      file: VirtualFileRef,
      lines: Seq[String],
      imports: Seq[String],
      offset: Int
  ): LazyClassLoaded[LoadedSbtFile] = {
    // TODO - Store the file on the LoadedSbtFile (or the parent dir) so we can accurately do
    //        detection for which project project manipulations should be applied.
    val name = file match
      case file: PathBasedFile => file.toPath.toString
      case file                => file.id
    val parsed = parseConfiguration(file, lines, imports, offset)
    val (importDefs, definitions) =
      if (parsed.definitions.isEmpty) (Nil, DefinedSbtValues.empty)
      else {
        val definitions =
          evaluateDefinitions(eval, name, parsed.imports, parsed.definitions, Some(file))
        val imp = BuildUtilLite.importAllRoot(definitions.enclosingModule :: Nil)
        (imp, DefinedSbtValues(definitions))
      }
    val allImports = importDefs.map(s => (s, -1)) ++ parsed.imports
    val dslEntries = parsed.settings map { case (dslExpression, range) =>
      evaluateDslEntry(eval, name, allImports, dslExpression, range)
    }

    // TODO:
    // eval.unlinkDeferred()

    // Tracks all the files we generated from evaluating the sbt file.
    val allGeneratedFiles: Seq[Path] = (definitions.generated ++ dslEntries.flatMap(_.generated))
    loader => {
      val projects = {
        val compositeProjects = definitions
          .values(loader)
          .collect { case p: CompositeProject => p }
        // todo: resolveBase?
        CompositeProject.expand(compositeProjects) // .map(resolveBase(file.getParentFile, _))
      }
      val loadedDslEntries = dslEntries.map(_.result.apply(loader))
      val settings = loadedDslEntries.collect { case DslEntry.ProjectSettings(s) => s }.flatten
      val manipulations = loadedDslEntries.collect { case DslEntry.ProjectManipulation(f) => f }
      // TODO -get project manipulations.
      new LoadedSbtFile(
        settings,
        projects,
        importDefs,
        manipulations,
        definitions,
        allGeneratedFiles
      )
    }
  }

  /** move a project to be relative to this file after we've evaluated it. */
  private[this] def resolveBase(f: File, p: Project) =
    p.copy(base = IO.resolve(f, p.base))

  def addOffset(offset: Int, lines: Seq[(String, Int)]): Seq[(String, Int)] =
    lines.map { case (s, i) => (s, i + offset) }

  def addOffsetToRange(offset: Int, ranges: Seq[(String, LineRange)]): Seq[(String, LineRange)] =
    ranges.map { case (s, r) => (s, r shift offset) }

  /**
   * The name of the class we cast DSL "setting" (vs. definition) lines to.
   */
  val SettingsDefinitionName = {
    val _ =
      classOf[DslEntry] // this line exists to try to provide a compile-time error when the following line needs to be changed
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
  private[sbt] def evaluateDslEntry(
      eval: Eval,
      name: String,
      imports: Seq[(String, Int)],
      expression: String,
      range: LineRange
  ): TrackedEvalResult[DslEntry] = {
    // TODO - Should we try to namespace these between.sbt files?  IF they hash to the same value, they may actually be
    // exactly the same setting, so perhaps we don't care?
    val result =
      try {
        eval.eval(
          expression,
          imports = new EvalImports(imports.map(_._1)), // name
          srcName = name,
          tpeName = Some(SettingsDefinitionName),
          line = range.start
        )
      } catch {
        case e: EvalException => throw new MessageOnlyException(e.getMessage)
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
  def evaluateSetting(
      eval: Eval,
      name: String,
      imports: Seq[(String, Int)],
      expression: String,
      range: LineRange
  ): LazyClassLoaded[Seq[Setting[_]]] =
    evaluateDslEntry(eval, name, imports, expression, range).result andThen {
      case DslEntry.ProjectSettings(values) => values
      case _                                => Nil
    }

  /**
   * Splits a set of lines into (imports, expressions).  That is,
   * anything on the right of the tuple is a scala expression (definition or setting).
   */
  private[sbt] def splitExpressions(
      file: VirtualFileRef,
      lines: Seq[String]
  ): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    val split = SbtParser(file, lines)
    // TODO - Look at pulling the parsed expression trees from the SbtParser and stitch them back into a different
    // scala compiler rather than re-parsing.
    (split.imports, split.settings)

  private[this] def splitSettingsDefinitions(
      lines: Seq[(String, LineRange)]
  ): (Seq[(String, LineRange)], Seq[(String, LineRange)]) =
    lines partition { case (line, _) => isDefinition(line) }

  private[this] def isDefinition(line: String): Boolean = {
    val trimmed = line.trim
    DefinitionKeywords.exists(trimmed startsWith _)
  }

  private[this] def extractedValTypes: Seq[String] =
    Seq(
      classOf[CompositeProject],
      classOf[InputKey[_]],
      classOf[TaskKey[_]],
      classOf[SettingKey[_]]
    ).map(_.getName)

  private[this] def evaluateDefinitions(
      eval: Eval,
      name: String,
      imports: Seq[(String, Int)],
      definitions: Seq[(String, LineRange)],
      file: Option[VirtualFileRef],
  ): EvalDefinitions = {
    val convertedRanges = definitions.map { case (s, r) => (s, r.start to r.end) }
    eval.evalDefinitions(
      convertedRanges,
      new EvalImports(imports.map(_._1)), // name
      name,
      // file,
      extractedValTypes
    )
  }
}

object BuildUtilLite:
  /** Import just the names. */
  def importNames(names: Seq[String]): Seq[String] =
    if (names.isEmpty) Nil else names.mkString("import ", ", ", "") :: Nil

  /** Prepend `_root_` and import just the names. */
  def importNamesRoot(names: Seq[String]): Seq[String] = importNames(names map rootedName)

  /** Wildcard import `._` for all values. */
  def importAll(values: Seq[String]): Seq[String] = importNames(values map { _ + "._" })
  def importAllRoot(values: Seq[String]): Seq[String] = importAll(values map rootedName)
  def rootedName(s: String): String = if (s contains '.') "_root_." + s else s
end BuildUtilLite

object Index {
  def taskToKeyMap(data: Settings[Scope]): Map[Task[_], ScopedKey[Task[_]]] = {

    val pairs = data.scopes flatMap (scope =>
      data.data(scope).entries collect { case AttributeEntry(key, value: Task[_]) =>
        (value, ScopedKey(scope, key.asInstanceOf[AttributeKey[Task[_]]]))
      }
    )

    pairs.toMap[Task[_], ScopedKey[Task[_]]]
  }

  def allKeys(settings: Seq[Setting[_]]): Set[ScopedKey[_]] = {
    val result = new java.util.HashSet[ScopedKey[_]]
    settings.foreach { s =>
      if (!s.key.key.isLocal && result.add(s.key)) {
        s.dependencies.foreach(k => if (!k.key.isLocal) result.add(s.key))
      }
    }
    result.asScala.toSet
  }

  def attributeKeys(settings: Settings[Scope]): Set[AttributeKey[_]] =
    settings.data.values.flatMap(_.keys).toSet[AttributeKey[_]]

  def stringToKeyMap(settings: Set[AttributeKey[_]]): Map[String, AttributeKey[_]] =
    stringToKeyMap0(settings)(_.label)

  private[this] def stringToKeyMap0(
      settings: Set[AttributeKey[_]]
  )(label: AttributeKey[_] => String): Map[String, AttributeKey[_]] = {
    val multiMap = settings.groupBy(label)
    val duplicates = multiMap.iterator
      .collect { case (k, xs) if xs.size > 1 => (k, xs.map(_.tag)) }
      .collect { case (k, xs) if xs.size > 1 => (k, xs) }
      .toVector
    if duplicates.isEmpty then multiMap.collect { case (k, v) if validID(k) => (k, v.head) }.toMap
    else
      val duplicateStr = duplicates
        .map { case (k, tps) => s"'$k' (${tps.mkString(", ")})" }
        .mkString(",")
      sys.error(s"Some keys were defined with the same name but different types: $duplicateStr")
  }

  private[this] type TriggerMap = collection.mutable.HashMap[TaskId[?], Seq[TaskId[?]]]

  def triggers(ss: Settings[Scope]): Triggers = {
    val runBefore = new TriggerMap
    val triggeredBy = new TriggerMap
    for
      a <- ss.data.values
      case AttributeEntry(_, base: Task[?]) <- a.entries
    do
      def update(map: TriggerMap, key: AttributeKey[Seq[Task[?]]]): Unit =
        base.info.attributes.get(key).getOrElse(Seq.empty).foreach { task =>
          map(task) = base +: map.getOrElse(task, Nil)
        }
      update(runBefore, Def.runBefore)
      update(triggeredBy, Def.triggeredBy)
    val onComplete = (GlobalScope / Def.onComplete).get(ss).getOrElse(() => ())
    new Triggers(runBefore, triggeredBy, map => { onComplete(); map })
  }

}
