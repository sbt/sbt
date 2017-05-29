package sbt
package compiler

import scala.tools.nsc.{ CompilerCommand, Global, Phase, Settings }
import scala.reflect.internal.util.BatchSourceFile
import scala.tools.nsc.interpreter.AbstractFileClassLoader
import scala.tools.nsc.io.{ AbstractFile, PlainFile, VirtualDirectory }
import scala.tools.nsc.ast.parser.Tokens.{ EOF, NEWLINE, NEWLINES, SEMI }
import scala.tools.nsc.reporters.{ ConsoleReporter, Reporter }
import java.io.File
import java.nio.ByteBuffer
import java.net.URLClassLoader

import scala.annotation.tailrec

/**
 * Represents the imports that are present to evaluate an expression.
 *
 * @param exprs The import expressions with their pertinent line numbers.
 * @param srcName The name of the source where these expressions are.
 */
final class EvalImports(val exprs: Seq[(String, Int)], val srcName: String)

/**
 * Represents the result of evaluating a Scala expression.
 *
 * @param tpe Inferred type of the expression by the compiler.
 * @param getValue A function that returns the value of the expression. This
 *                 function expects a class loader that provides the classes
 *                 that were present in the classpath at compile time.
 * @param generated The compiled class files related to the evaluated code.
 * @param enclosingModule The name of the synthetic enclosing module.
 */
final class EvalResult(val tpe: String,
  val getValue: ClassLoader => Any,
  val generated: Seq[File],
  val enclosingModule: String)

/**
 * Represents the evaluated sbt DSL definitions.
 *
 * These definitions are wrapped in synthetic top-level modules that can then
 * be imported in all the use sites.
 *
 * @param loader A function to get a new class loader that contains the
 *               synthetic enclosing module. It needs a parent class loader.
 * @param names The names of the compiled definitions.
 * @param generated The compiled class files related to the evaluated code.
 * @param enclosingModule The name of the synthetic enclosing module.
 */
final class EvalDefinitions(val loader: ClassLoader => ClassLoader,
    val names: Seq[String],
    val generated: Seq[File],
    val enclosingModule: String) {

  /**
   * Get the values of all the definitions.
   *
   * @param parent The parent class loader on which to load the module defs.
   * @return The values of all the evaluated definitions.
   */
  def values(parent: ClassLoader): Seq[Any] = {
    // TODO(jvican): This should be further optimized.
    val module = Eval.getModule(enclosingModule, loader(parent))
    for (n <- names)
      yield module.getClass.getMethod(n).invoke(module)
  }
}

final class EvalSettings(val loader: ClassLoader => ClassLoader)

final class EvalSbtFile(
    val loader: ClassLoader => ClassLoader,
    val definitionsNames: Seq[String],
    val settingsNames: Seq[String],
    val generated: Seq[File],
    val enclosingModule: String) {

  def loadDefinitionsAndSettings(parent: ClassLoader): (Seq[Any], Seq[Any]) = {
    val module = Eval.getModule(enclosingModule, loader(parent))
    val definitions = definitionsNames.map(name => Eval.getValue(module, name))
    val settings = settingsNames.map(name => Eval.getValue(module, name))
    definitions -> settings
  }

  def getEvalDefinitions: EvalDefinitions =
    new EvalDefinitions(loader, definitionsNames, generated, enclosingModule)
}

/**
 * Represents a compilation error.
 *
 * @param msg The error message captured from the compiler.
 */
final class EvalException(msg: String) extends RuntimeException(msg)

/**
 * Represents the evaluation logic of sbt files.
 *
 * This method is not thread-safe since it reuses the same [[Global]] instance.
 *
 * @param optionsWithoutClasspath Scalac options that don't include 'cp'.
 * @param classpath The classpath to compile and evaluate sbt code.
 * @param mkReporter A function that turns settings into a reporter.
 * @param target The directory where the compiled class files are saved.
 */
final class Eval(optionsWithoutClasspath: Seq[String],
  classpath: Seq[File],
  mkReporter: Settings => Reporter,
  target: Option[File])
    extends SbtParser
    with SbtDefinitions
    with SbtCodegen
    with SbtHashGen {

  def this(mkReporter: Settings => Reporter, target: Option[File]) =
    this(Nil, IO.classLocationFile[Product] :: Nil, mkReporter, target)

  def this() = this(s => new ConsoleReporter(s), None)

  /**
   * Represent a subclass of [[Global]] to mutate the current run.
   *
   * This is necessary to control the Scalac pipeline.
   */
  final class EvalGlobal(settings: Settings, reporter: Reporter)
      extends Global(settings, reporter) {
    override def currentRun: Run = curRun
    var curRun: Run = null
  }

  /* *********************************************************************** */
  /* ******** Initialize the target, settings, reporter and global ********* */
  /* *********************************************************************** */

  // TODO: provide a way to cleanup target directory
  target.foreach(IO.createDirectory)

  val scalacOptions: Seq[String] = {
    val completeClasspath = Path.makeString(classpath ++ target.toList)
    "-cp" +: completeClasspath +: optionsWithoutClasspath
  }

  lazy val settings: Settings = {
    val settings0 = new Settings(println)
    // Don't touch this line if you like order in the universe
    new CompilerCommand(scalacOptions.toList, settings0)
    settings0
  }

  lazy val reporter: Reporter = mkReporter(settings)
  lazy val global: EvalGlobal = new EvalGlobal(settings, reporter)

  import global._

  /* *********************************************************************** */
  /* ************ Define the main logic of the sbt evaluation ************** */
  /* *********************************************************************** */

  // This method is used outside of this class
  private[sbt] def unlinkDeferred(): Unit = {
    toUnlinkLater foreach unlink
    toUnlinkLater = Nil
  }

  private[this] var toUnlinkLater = List[Symbol]()
  private[this] def unlink(sym: Symbol) = sym.owner.info.decls.unlink(sym)

  private val DefaultSetting = "<setting>"
  private val DefaultStartLine = 0

  /** Misnomer, it should be called `evalSetting` for clarity. */
  @deprecated("Use `Eval.evalSetting` instead.", "0.13.14")
  def eval(expression: String,
    imports: EvalImports = noImports,
    tpeName: Option[String] = None,
    srcName: String = DefaultSetting,
    line: Int = DefaultStartLine): EvalResult =
    evalSetting(expression, imports, tpeName, srcName, line)

  /**
   * Evaluate a sbt setting.
   *
   * @param expression The expression to be evaluated.
   * @param imports The imports present during compilation.
   * @param tpeName The expected type of the setting.
   * @param srcName The name of the source file.
   * @param line The line where the setting comes from.
   * @return An instance of [[EvalResult]].
   */
  def evalSetting(expression: String,
    imports: EvalImports = noImports,
    tpeName: Option[String] = None,
    srcName: String = DefaultSetting,
    line: Int = DefaultStartLine): EvalResult = {

    // Define the settings evaluator
    val evaluator = new Evaluator[String] {
      def unlink = true
      def makeUnit: CompilationUnit = mkUnit(srcName, line, expression)

      def read(file: File): String = IO.read(file)
      def write(value: String, f: File): Unit = IO.write(f, value)
      def extraHash = ""

      def unitBody(unit: CompilationUnit,
        importTrees: Seq[Tree],
        moduleName: String): Tree = {
        val (parser, tree) = parse(unit, settingErrorStrings, _.expr())
        val tpt: Tree = expectedType(tpeName)
        wrapInModule(parser, importTrees, tree, tpt, moduleName)
      }

      def extra(run: Run, unit: CompilationUnit): String =
        atPhase(run.typerPhase.next) { (new TypeExtractor).getType(unit.body) }
    }

    val ir = evalCommon(expression :: Nil, imports, tpeName.toList, evaluator)
    val valueLoader = (cl: ClassLoader) =>
      Eval.getValue[Any](ir.enclosingModule, ir.loader(cl))
    new EvalResult(ir.extra, valueLoader, ir.generated, ir.enclosingModule)
  }

  @deprecated("Use alternative `evalDefinitions` definition.", "0.13.14")
  def evalDefinitions(definitions: Seq[(String, scala.Range)],
    imports: EvalImports,
    srcName: String,
    target: Option[File],
    valTypes: Seq[String]): EvalDefinitions = {
    require(target.isEmpty,
      "The target directory for `evalDefinitions` cannot be empty.")
    evalDefinitions(definitions, imports, srcName, target.get, valTypes)
  }

  /**
   * Evaluate sbt definitions.
   *
   * Sbt definitions are anything that [[parseDefinitions]] parse successfully.
   *
   * @param definitions The definitions to be compiled.
   * @param imports The imports accessible to the definitions.
   * @param srcName The name of the source file.
   * @param target The target directory where class files are generated.
   * @param valTypes The expected types of the definitions.
   * @return An instance of [[EvalDefinitions]].
   */
  def evalDefinitions(definitions: Seq[(String, scala.Range)],
    imports: EvalImports,
    srcName: String,
    target: File,
    valTypes: Seq[String]): EvalDefinitions = {
    require(definitions.nonEmpty, "Definitions to evaluate cannot be empty.")

    val evaluator = new Evaluator[Seq[String]] {
      lazy val (fullUnit, defUnits) = mkUnitsForDefs(srcName, definitions)
      def makeUnit: CompilationUnit = fullUnit
      def unlink = false
      def unitBody(unit: CompilationUnit,
        importTrees: Seq[Tree],
        moduleName: String): Tree = {
        val fullParser = new syntaxAnalyzer.UnitParser(unit)
        val trees = defUnits flatMap parseDefinitions
        syntheticModule(fullParser, importTrees, trees.toList, moduleName)
      }

      def extra(run: Run, unit: CompilationUnit): List[String] = {
        atPhase(run.typerPhase.next) {
          new ValExtractor(valTypes.toSet).getValNames(unit.body)
        }
      }

      def read(file: File): List[String] = IO.readLines(file)
      def write(value: Seq[String], file: File): Unit =
        IO.writeLines(file, value)
      def extraHash: String = target.getAbsolutePath
    }

    val ir = evalCommon(definitions.map(_._1), imports, Nil, evaluator)
    new EvalDefinitions(ir.loader, ir.extra, ir.generated, ir.enclosingModule)
  }

  /**
   * Hosts the common logic for evaluating sbt settings and definitions.
   *
   * @param lines The lines of the source file that are part of the unit.
   * @param imports The imports to be accessible at compilation.
   * @param expectedTypes The expected types.
   * @param evaluator An evaluator that returns compiler information.
   * @tparam T The compiler information that we expect it to return.
   * @return An instance of [[IR]] that is later process by the use sites.
   */
  private[this] def evalCommon[T](lines: Seq[String],
    imports: EvalImports,
    expectedTypes: Seq[String],
    evaluator: Evaluator[T]): IR[T] = {
    // Generate the name of the module from the hash of all the inputs
    val importExprs = imports.exprs.map(_._1)
    val extraH = evaluator.extraHash
    val inputs = CompilationInputs(lines, target, scalacOptions, classpath)
    val hash = hashInputs(inputs, importExprs, expectedTypes, extraH)
    val moduleName = makeModuleName(hash)

    // Get the evaluation information and the loader generator
    val (extra, loader) = {
      target match {
        case Some(back) if classExists(back, moduleName) =>
          val loader = (parent: ClassLoader) =>
            new URLClassLoader(Array(back.toURI.toURL), parent)
          val extra = evaluator.read(cacheFile(back, moduleName))
          (extra, loader)
        case _ =>
          reporter.reset()
          val unit = evaluator.makeUnit
          val run = new Run { override def units = (unit :: Nil).iterator }
          try {
            compileAndLoad(run, unit, imports, target, moduleName, evaluator)
          } finally { unlinkAll(run, evaluator) }
      }
    }

    // Create the intermediate representation for further manipulation
    val generatedFiles = getGeneratedFiles(target, moduleName)
    new IR(extra, loader, generatedFiles, moduleName)
  }

  private[this] def compileAndLoad[T](
    run: Run,
    unit: CompilationUnit,
    imports: EvalImports,
    target: Option[File],
    moduleName: String,
    evaluator: Evaluator[T]): (T, ClassLoader => ClassLoader) = {

    @tailrec def compile(phase: Phase): Unit = {
      globalPhase = phase
      if (phase == null || phase == phase.next || reporter.hasErrors) ()
      else {
        atPhase(phase) { phase.run }
        compile(phase.next)
      }
    }

    global.curRun = run
    run.currentUnit = unit
    val dir = outputDirectory(target)
    settings.outputDirs.setSingleOutput(dir)

    val importTrees = imports.exprs.flatMap {
      case (importExpr, line) =>
        parseImport(mkUnit(imports.srcName, line, importExpr))
    }

    unit.body = evaluator.unitBody(unit, importTrees, moduleName)
    compile(run.namerPhase)
    Eval.checkError(reporter, "Type error in expression")

    val extra = evaluator.extra(run, unit)
    for (cls <- target) evaluator.write(extra, cacheFile(cls, moduleName))
    val loader = (host: ClassLoader) => new AbstractFileClassLoader(dir, host)
    (extra, loader)
  }

  def load(dir: AbstractFile, moduleName: String): ClassLoader => Any = {
    (parent: ClassLoader) =>
      Eval.getValue[Any](moduleName, new AbstractFileClassLoader(dir, parent))
  }

  def loadPlain(dir: File, moduleName: String): ClassLoader => Any = {
    (parent: ClassLoader) =>
      Eval.getValue[Any](moduleName,
        new URLClassLoader(Array(dir.toURI.toURL), parent))
  }

  def evalSbtFile(
    definitions: Seq[(String, scala.Range)],
    settings: Seq[(String, scala.Range)],
    settingsTypes: Seq[Option[String]],
    imports: EvalImports,
    srcName: String,
    target: File,
    definitionTypesToExtract: Seq[String]): EvalSbtFile = {

    val settingsNames: Seq[TermName] = generateSettingNamesFor(settings.size)
    val evaluator = new Evaluator[SbtMetadata] {
      val (_, settingsUnits) = mkUnitsForDefs(srcName, settings)
      val (_, defUnits) = mkUnitsForDefs(srcName, definitions)

      // Order expressions in definition order assuming start line is unique
      val orderedExpressions: Seq[(String, Range)] =
        (settings ++ definitions).sortBy { case (_, range) => range.start }
      val (fullUnit, _) = mkUnitsForDefs(srcName, orderedExpressions)

      def unlink = true
      def makeUnit: CompilationUnit = fullUnit

      private val sectionDelimiter = "~~~~~"

      def read(file: File): SbtMetadata = {
        val lines = IO.read(file).lines.toList
        val at = lines.indexOf(sectionDelimiter)
        val (settingsTypes, delimWithDefinitions) = lines.splitAt(at)
        SbtMetadata(delimWithDefinitions.tail, settingsTypes)
      }

      def write(value: SbtMetadata, f: File): Unit = {
        // TODO(jvican): Make sure this goes okay when definitions are empty
        val settingsSection = value.settingsTypes.mkString("\n")
        val definitionsSection = value.definitionNames.mkString("\n")
        IO.write(f, s"$settingsSection\n$sectionDelimiter\n$definitionsSection")
      }

      def extraHash: String = target.getAbsolutePath

      def unitBody(unit: CompilationUnit,
        imports: Seq[Tree],
        moduleName: String): Tree = {

        val parser = new syntaxAnalyzer.UnitParser(unit)
        val parsedSettings = settingsUnits.map(parseSetting)
        val defTrees = defUnits.flatMap(parseDefinitions)
        val expectedSettingsTypes = settingsTypes.map(expectedType)
        val settingsWithTypes = parsedSettings.zip(expectedSettingsTypes)
        val settingTrees = settingsNames.zip(settingsWithTypes)

        wrapInModule(parser, imports, defTrees, settingTrees, moduleName)
      }

      /** Gets the additional information to be stored for later processing. */
      def extra(run: Run, unit: CompilationUnit): SbtMetadata = {
        val (settingsTypes, definitionNames) = atPhase(run.typerPhase.next) {
          val body = unit.body
          val settingsTypes = new TypesExtractor(settingsNames).getTypes(body)
          val toExtract = definitionTypesToExtract.toSet
          val definitionNames = new ValExtractor(toExtract).getValNames(body)
          settingsTypes -> definitionNames
        }
        SbtMetadata(definitionNames, settingsTypes)
      }
    }

    val allLines = definitions.map(_._1) ++ settings.map(_._1)
    val allTypes = settingsTypes.flatten
    val ir = evalCommon(allLines, imports, allTypes, evaluator)

    val compilerInfo = ir.extra
    val definitionNames = compilerInfo.definitionNames
    new EvalSbtFile(
      ir.loader,
      definitionNames,
      settingsNames.map(_.decoded),
      ir.generated,
      ir.enclosingModule
    )
  }

  /* ************************************************************************ */
  /* ***************************** Utilities ******************************** */
  /* ************************************************************************ */

  private[this] def cacheFile(base: File, moduleName: String): File =
    new File(base, moduleName + ".cache")

  private[this] def unlinkAll(run: Run, evaluator: Evaluator[_]): Unit = {
    for ((sym, _) <- run.symSource) {
      if (evaluator.unlink) unlink(sym)
      else toUnlinkLater ::= sym
    }
  }

  private[this] def expectedType(tpeName: Option[String]): Tree = {
    tpeName match {
      case Some(tpe) =>
        val unit = mkUnit("<expected-type>", DefaultStartLine, tpe)
        parseType(unit, tpe)
      case None => TypeTree(NoType)
    }
  }

  private[this] def outputDirectory(target: Option[File]): AbstractFile = {
    target match {
      case None      => new VirtualDirectory("<virtual>", None);
      case Some(dir) => new PlainFile(dir)
    }
  }

  private[this] final class TypeExtractor extends Traverser {
    private[this] var result = ""
    def getType(t: Tree) = { result = ""; traverse(t); result }
    override def traverse(tree: Tree): Unit = tree match {
      case d: DefDef if d.symbol.nameString == Eval.WrapValName =>
        result = d.symbol.tpe.finalResultType.toString
      case _ => super.traverse(tree)
    }
  }

  /**
   * Extract the types of the methods whose name is contained in `names`.
   *
   * @param names The names of the definitions whose type should be extracted.
   */
  private[this] final class TypesExtractor(names: Seq[TermName])
      extends Traverser {
    import scala.collection.mutable
    private[this] val types = mutable.HashMap.empty[String, String]

    def getTypes(t: Tree): Seq[String] = {
      types.clear()
      traverse(t)
      // Return types in order of definition
      // Remember that names are <WrapValName><index>
      types.toSeq.sortBy(_._1).map(_._2)
    }

    override def traverse(tree: Tree): Unit = {
      tree match {
        case d: DefDef if names.contains(d.symbol.name.decodedName) =>
          val sym = d.symbol
          val definitionName = sym.name.decodedName.toString
          types += definitionName -> sym.tpe.finalResultType.toString
        case _ => super.traverse(tree)
      }
    }
  }

  /**
   * Tree traverser that obtains the names of vals in a top-level module
   * whose type is the subtype of one of the provided `tpes`.
   */
  private[this] final class ValExtractor(definitionTypes: Set[String]) extends Traverser {
    private[this] var vals = List[String]()
    def getValNames(t: Tree): List[String] = { vals = Nil; traverse(t); vals }

    // Removed in commit `e5b050814deb2e7e1d6d05511d3a6cb6b013b549`.
    private def isTopLevelModule(s: Symbol): Boolean =
      s.hasFlag(reflect.internal.Flags.MODULE) && s.owner.isPackageClass

    /**
     * Check whether a val definition can be extracted or not.
     *
     * A valid val has the following properties:
     *   - Its owner is a top-level tree.
     *   - Its name does not contain `WrapValName`.
     *   - Its type is a subtype of the ones provided in `definitionTypes`.
     */
    private def isValidVal(valDef: ValDef): Boolean = {
      val sym = valDef.symbol
      isTopLevelModule(sym.owner) &&
        !sym.nameString.contains(Eval.WrapValName) && {
          val tpe = valDef.tpt.tpe
          tpe.baseClasses.exists { sym =>
            definitionTypes.contains(sym.fullName)
          }
        }
    }

    override def traverse(tree: Tree): Unit = tree match {
      case t: ValDef if isValidVal(t) =>
        vals ::= nme.localToGetter(t.name).encoded
      case _ => super.traverse(tree)
    }
  }

  // TODO: reuse the code from `Analyzer`
  private[this] def classExists(dir: File, name: String) =
    new File(dir, name + ".class").exists

  private[this] def getGeneratedFiles(target: Option[File],
    moduleName: String): Seq[File] = {
    target match {
      case Some(dir) =>
        dir.listFiles {
          new java.io.FilenameFilter {
            def accept(dir: File, s: String): Boolean =
              s.contains(moduleName)
          }
        }
      case None => Nil
    }
  }

  private[this] def noImports = new EvalImports(Nil, "")
  private[this] def mkUnit(srcName: String, firstLine: Int, s: String) =
    new CompilationUnit(new EvalSourceFile(srcName, firstLine, s))

  private[this] def createUnitAtLines(srcName: String,
    content: String,
    lineMap: Array[Int]): CompilationUnit =
    new CompilationUnit(fragmentSourceFile(srcName, content, lineMap))

  /**
   * Construct a [[CompilationUnit]] for each definition and another one
   * for batch compilation.
   *
   * Every compilation unit is then used to independently parse the
   * definition into a [[Tree]]. Additionally, a [[CompilationUnit]] is
   * constructed to use it for combined compilation after parsing. This is
   * useful because definitions can all be compiled at once.
   */
  private[this] def mkUnitsForDefs(srcName: String,
    definitions: Seq[(String, scala.Range)]): (CompilationUnit, Seq[CompilationUnit]) = {
    import collection.mutable.ListBuffer
    val lines = new ListBuffer[Int]()
    val defs = new ListBuffer[CompilationUnit]()
    val fullContent = new java.lang.StringBuilder()
    for ((defString, range) <- definitions) {
      defs += createUnitAtLines(srcName, defString, range.toArray)
      fullContent.append(defString)
      lines ++= range
      fullContent.append("\n\n")
      lines ++= (range.end :: range.end :: Nil)
    }
    val fullUnit =
      createUnitAtLines(srcName, fullContent.toString, lines.toArray)
    (fullUnit, defs.toSeq)
  }

  /**
   * Create a "virtual" source file that will show only a fragment of a
   * the contents of an existing user-defined sbt source file.
   *
   * @param srcName The name of the virtual source file.
   * @param content The contents of the original user-defined source file.
   * @param lineMap The lines to contain, need to be ordered but not consecutive.
   * @return A file that will show only the `lineMap` of `content`.
   */
  private[this] def fragmentSourceFile(srcName: String,
    content: String,
    lineMap: Array[Int]) = {
    new BatchSourceFile(srcName, content) {
      override def lineToOffset(line: Int): Int =
        super.lineToOffset(lineMap.indexWhere(_ == line) max 0)

      override def offsetToLine(offset: Int): Int =
        index(lineMap, super.offsetToLine(offset))

      private[this] def index(a: Array[Int], i: Int): Int =
        if (i < 0 || i >= a.length) 0 else a(i)

      /** Return only the name of the file since we use it to populate `SourceFile`. */
      override def toString: String = new File(srcName).getName
    }
  }
}

private[sbt] object Eval {
  private[sbt] def checkError(reporter: Reporter, label: String) =
    if (reporter.hasErrors) throw new EvalException(label)

  import scala.collection.mutable
  private[sbt] val moduleCache = mutable.WeakHashMap.empty[ClassLoader, Any]

  /** The name of the synthetic val in the synthetic module that an expression is assigned to. */
  final val WrapValName = "$sbtdef"

  /**
   * Gets the value of the expression wrapped in module `objectName`.
   *
   * @param objectName The name of the synthetic sbt object. This module
   *                   name should not include the trailing `$`.
   * @param loader The class loader on which `objectName` is accessible.
   * @tparam T The expected type of the value.
   * @return The value typed as [[T]].
   */
  def getValue[T](objectName: String, loader: ClassLoader): T = {
    val module = getModule(objectName, loader)
    val accessor = module.getClass.getMethod(WrapValName)
    val value = accessor.invoke(module)
    value.asInstanceOf[T]
  }

  private[sbt] def getValue(moduleInstance: Any, name: String): Any = {
    val accessor = moduleInstance.getClass.getMethod(name)
    val value = accessor.invoke(moduleInstance)
    value
  }

  /**
   * Gets the top-level module `moduleName` from the provided class `loader`.
   *
   * @param moduleName The name of the target module name. This name should
   *                   not include the trailing `$`.
   * @param loader The class loader on which `objectName` is accessible.
   * @return The module if found, otherwise null.
   */
  def getModule(moduleName: String, loader: ClassLoader): Any = {
    val clazz = Class.forName(moduleName + "$", true, loader)
    clazz.getField("MODULE$").get(null)
  }

}

/** Defines utilities to parse `*.sbt` files. */
private[sbt] trait SbtParser {
  val global: Global
  val reporter: Reporter
  import global.{ CompilationUnit, Tree }
  import global.syntaxAnalyzer.UnitParser

  protected class ParseErrorStrings(val base: String,
    val extraBlank: String,
    val missingBlank: String,
    val extraSemi: String)

  protected def definitionErrorStrings = new ParseErrorStrings(
    base = "Error parsing definition.",
    extraBlank = "  Ensure that there are no blank lines within a definition.",
    missingBlank = "  Ensure that definitions are separated by blank lines.",
    extraSemi =
      "  A trailing semicolon is not permitted for standalone definitions."
  )

  protected def settingErrorStrings = new ParseErrorStrings(
    base = "Error parsing expression.",
    extraBlank = "  Ensure that there are no blank lines within a setting.",
    missingBlank = "  Ensure that settings are separated by blank lines.",
    extraSemi =
      "  Note that settings are expressions and do not end with semicolons.  (Semicolons are fine within {} blocks, however.)"
  )

  /**
   * Parse a compilation unit according to `parserLogic`.
   *
   * This method performs check on the final parser state to give concrete
   * parser errors to the user, especifically on '\n'-delimited sbt files.
   *
   * It's generic because different use sites inject their own `parserLogic`.
   */
  protected def parse[T](unit: CompilationUnit,
    errors: ParseErrorStrings,
    parserLogic: UnitParser => T): (UnitParser, T) = {
    val parser = new UnitParser(unit)
    val tree = parserLogic(parser)
    val extra = parser.in.token match {
      case EOF => errors.extraBlank
      case _   => ""
    }
    Eval.checkError(reporter, errors.base + extra)

    parser.accept(EOF)
    val extra2 = parser.in.token match {
      case SEMI               => errors.extraSemi
      case NEWLINE | NEWLINES => errors.missingBlank
      case _                  => ""
    }
    Eval.checkError(reporter, errors.base + extra2)

    (parser, tree)
  }

  protected def parseType(unit: CompilationUnit, tpe: String): Tree = {
    val tpeParser = new UnitParser(unit)
    val tpt0: Tree = tpeParser.typ()
    tpeParser.accept(EOF)
    Eval.checkError(reporter, "Error parsing expression type.")
    tpt0
  }

  protected def parseImport(importUnit: CompilationUnit): Seq[Tree] = {
    val parser = new UnitParser(importUnit)
    val trees: Seq[Tree] = parser.importClause()
    parser.accept(EOF)
    Eval.checkError(reporter, "Error parsing imports for expression.")
    trees
  }

  protected def parseDefinitions(du: CompilationUnit): Seq[Tree] =
    parse(du, definitionErrorStrings, parseDefinitions)._2

  protected def parseSetting(unit: CompilationUnit): Tree =
    parse(unit, settingErrorStrings, _.expr())._2

  /**
   * Parse sbt definitions.
   *
   * This parser considers the following as definitions:
   *   `def`s, `val`s, `lazy val`s, classes, traits and objects.
   *
   * Note that even parsing classes, trait, and objects is supported by this
   * method, they will never be parsed because they are not valid sbt
   * expressions and `EvaluateConfigurations` prunes them before hitting this.
   */
  private[this] def parseDefinitions(parser: UnitParser): Seq[Tree] = {
    var defs = parser.nonLocalDefOrDcl
    parser.acceptStatSepOpt()
    while (!parser.isStatSeqEnd) {
      val next = parser.nonLocalDefOrDcl
      defs ++= next
      parser.acceptStatSepOpt()
    }
    defs
  }
}

private[sbt] trait SbtCodegen {
  val global: Global
  import global._
  import syntaxAnalyzer.UnitParser
  import Eval.WrapValName

  protected def makeModuleName(hash: String): String = "$" + Hash.halve(hash)

  /**
   * Create a synthetic module that contains `imports` and `members`.
   *
   * It produces a tree equivalent to:
   * {{{
   *   <imports>
   *   object <objectName> {
   *     <members>
   *   }
   * }}}
   *
   * @param parser A parser instance.
   * @param imports The imports accessible to the module's members.
   * @param members The members of the module.
   * @param moduleName The name of the synthetic module.
   * @return
   */
  protected def syntheticModule(parser: UnitParser,
    imports: Seq[Tree],
    members: List[Tree],
    moduleName: String): Tree = {
    // Generate an empty constructor for the module template
    val emptyName = nme.EMPTY.toTypeName
    val superCall: Apply =
      Apply(Select(Super(This(emptyName), emptyName), nme.CONSTRUCTOR), Nil)
    val body = Block(List(superCall), Literal(Constant(())))
    val emptyConstructor: DefDef =
      DefDef(NoMods, nme.CONSTRUCTOR, Nil, List(Nil), TypeTree(), body)

    // Generate the synthetic module
    val moduleDefinitions = emptyConstructor :: members
    def moduleBody: Template =
      Template(List(gen.scalaAnyRefConstr), emptyValDef, moduleDefinitions)
    def moduleDef = ModuleDef(NoMods, newTermName(moduleName), moduleBody)

    // Create a package around the generated module def and the imports
    val emptyPkg = parser.atPos(0, 0, 0) { Ident(nme.EMPTY_PACKAGE_NAME) }
    val allTrees = (imports :+ moduleDef).toList
    parser.makePackaging(0, emptyPkg, allTrees)
  }

  /**
   * Wrap the sbt pieces into an enclosing object with a concrete method.
   *
   * It produces a tree equivalent to:
   * {{{
   *   <imports>
   *   object <objectName> {
   *     def WrapValName: <tpt> = <tree>
   *   }
   * }}}
   *
   * @param parser A parser.
   * @param imports The imports to be used.
   * @param tree The tree to be wrapped in the `WrapValName` function.
   * @param tpt The return type of the wrapped function.
   * @param moduleName The name of the enclosing object.
   * @return The augmented tree.
   */
  def wrapInModule(parser: UnitParser,
    imports: Seq[Tree],
    tree: Tree,
    tpt: Tree,
    moduleName: String): Tree = {
    val method = DefDef(NoMods, newTermName(WrapValName), Nil, Nil, tpt, tree)
    syntheticModule(parser, imports, method :: Nil, moduleName)
  }

  /**
   * Generate the names for settings in order of the definition.
   *
   * @param size The number of settings to be named.
   * @return A sequence of named settings.
   */
  def generateSettingNamesFor(size: Int): Seq[TermName] =
    List.tabulate(size)(index => newTermName(s"$WrapValName$index"))

  type ExpectedSettingType = Tree
  type DefinedSetting = (Tree, ExpectedSettingType)

  /**
   * Wrap sbt definitions and settings into a concrete module.
   *
   * This is a new encoding for sbt builds that is done to be faster. The
   * previous encoding required compiling definitions first and then compiling
   * settings independently in the order of definition. This meant creating
   * a [[Run]] every time a setting was evaluated.
   *
   * It produces a tree equivalent to:
   * {{{
   *   object <objectName> {
   *     <imports>
   *     <definitions>
   *     <setting-definitions>
   *   }
   * }}}
   *
   * where a `setting-definition` has the following encoding:
   *
   * {{{
   *   def WrapValName<index>: <tpt> = <tree>
   * }}}
   *
   * and a `definition` is just a [[Tree]].
   *
   * @param parser A parser.
   * @param imports The imports to be used.
   * @param definitions The definitions from the sbt file.
   * @param namedSettings The settings with expected types from sbt file.
   * @param moduleName The name of the enclosing object.
   * @return The augmented tree.
   */
  def wrapInModule(parser: UnitParser,
    imports: Seq[Tree],
    definitions: Seq[Tree],
    namedSettings: Seq[(TermName, DefinedSetting)],
    moduleName: String): Tree = {

    val settingsDefinitions = namedSettings.map {
      case (methodName, (tree, tpt)) =>
        DefDef(NoMods, methodName, Nil, Nil, tpt, tree)
    }

    val insideModule = definitions ++: settingsDefinitions.toList
    syntheticModule(parser, imports, insideModule, moduleName)
  }
}

private[sbt] trait SbtDefinitions {
  val global: Global
  import global.{ CompilationUnit, Tree, Run }

  /**
   * Define an "intermediate representation" for sbt definitions and settings.
   * This intermediate representation is a product of [[Evaluator]].
   *
   * @param extra The extra information extracted after evaluation.
   * @param loader A function that provides a class loader with all the
   *               compiled class files loaded.
   * @param generated The generated class files by [[Evaluator]].
   * @param enclosingModule The name of the synthetic enclosing module.
   * @tparam T The expected type of the expression.
   */
  protected final class IR[T](val extra: T,
    val loader: ClassLoader => ClassLoader,
    val generated: Seq[File],
    val enclosingModule: String)

  /**
   * Define an evaluator that represents how a sbt compilation unit should
   * be compiled by scalac.
   *
   * @tparam T The expected type of the evaluated expression.
   */
  protected trait Evaluator[T] {

    /** Hash of the extra information to avoid collisions. */
    def extraHash: String

    /**
     * Define a hook to extract additional information from the compiler.
     *
     * This hook is called after the evaluation of the compilation unit.
     *
     * @param run The `Run` class that will compile `unit`.
     * @param unit The unit to be evaluated.
     * @return The read extra information of type [[T]]
     */
    def extra(run: Run, unit: CompilationUnit): T

    /**
     * Deserializes the extra information from a cache file.
     *
     * This method is only called if the inputs are the same.
     */
    def read(file: File): T

    /**
     * Serializes the extra information to a cache file.
     *
     * This method is only called if the inputs are the same.
     *
     * @param value The extra information to be serialized.
     * @param file The cache file.
     */
    def write(value: T, file: File): Unit

    /** Defines whether all evaluted top-level symbols should be unlinked. */
    def unlink: Boolean

    /**
     * Constructs the full compilation unit for this evaluation.
     *
     * This is used for error reporting during compilation. `unitBody`
     * does the parsing and may parse the tree from another source.
     */
    def makeUnit: CompilationUnit

    /**
     * Constructs the tree to be compiled.
     *
     * @param unit The full compilation unit from [[makeUnit]].
     * @param importTrees The import trees to be used.
     * @param moduleName The synthetic name of the enclosing module.
     * @return A tree representing the compilation unit to compile. This tree
     *         may not be parsed from the contents of `unit`.
     */
    def unitBody(unit: CompilationUnit,
      importTrees: Seq[Tree],
      moduleName: String): Tree
  }

  /**
   * Represents the user inputs for the compilation.
   *
   * @param content The lines to be compiled (contents of a source file).
   * @param target The target directory for class files.
   * @param scalacOptions The options for compilation.
   * @param classpath The compilation classpath.
   */
  case class CompilationInputs(content: Seq[String],
    target: Option[File],
    scalacOptions: Seq[String],
    classpath: Seq[File])

  /**
   * Defines a virtual source file with the contents starting from a line.
   *
   * @param name The name of the source file.
   * @param startLine The start line where the position starts.
   * @param contents The contents of the file.
   */
  protected final class EvalSourceFile(name: String,
    startLine: Int,
    contents: String)
      extends BatchSourceFile(name, contents) {
    override def lineToOffset(line: Int): Int =
      super.lineToOffset((line - startLine) max 0)
    override def offsetToLine(offset: Int): Int =
      super.offsetToLine(offset) + startLine
  }

  /**
   *
   * @param definitionNames
   * @param settingsTypes
   */
  case class SbtMetadata(
    definitionNames: Seq[String],
    settingsTypes: Seq[String])

}

/** Define utils to hash inputs to the sbt compiler. */
private[sbt] trait SbtHashGen { self: SbtDefinitions =>

  private val classDirFilter: FileFilter =
    DirectoryFilter || GlobFilter("*.class")

  /**
   * Hash the inputs.
   *
   * It includes the lines of the source files in the hash to avoid
   * conflicts where the exact same settings is defined in multiple evaluated
   * instances for a target, which can lead to issues with finding a previous
   * value on the classpath during compilation.
   *
   * @param compilationInputs The user-defined compilation inputs.
   * @param importExprs The imports to be used in the compilation unit.
   * @param typeName The type name.
   * @param extra Extra evaluation information.
   * @return
   */
  def hashInputs(compilationInputs: CompilationInputs,
    importExprs: Seq[String],
    tpes: Seq[String],
    extra: String): String = {
    val allBytes =
      stringSeqBytes(compilationInputs.content) ::
        optBytes(compilationInputs.target)(fileExistsBytes) ::
        stringSeqBytes(compilationInputs.scalacOptions) ::
        seqBytes(compilationInputs.classpath)(fileModifiedBytes) ::
        stringSeqBytes(importExprs) ::
        stringSeqBytes(tpes) ::
        bytes(extra) ::
        Nil
    Hash.toHex(Hash(bytes(allBytes)))
  }

  def optBytes[T](o: Option[T])(f: T => Array[Byte]): Array[Byte] =
    seqBytes(o.toSeq)(f)

  def seqBytes[T](s: Seq[T])(f: T => Array[Byte]): Array[Byte] =
    bytes(s.map(f))

  def stringSeqBytes(s: Seq[String]): Array[Byte] =
    seqBytes(s)(bytes)

  def fileExistsBytes(f: File): Array[Byte] =
    bytes(f.exists) ++ bytes(f.getAbsolutePath)
  def filesModifiedBytes(fs: Array[File]): Array[Byte] = {
    if (fs eq null) filesModifiedBytes(Array[File]())
    else seqBytes(fs)(fileModifiedBytes)
  }
  def fileModifiedBytes(f: File): Array[Byte] = {
    val fullContentsHash =
      if (f.isDirectory) filesModifiedBytes(f.listFiles(classDirFilter))
      else bytes(f.lastModified)
    fullContentsHash ++ bytes(f.getAbsolutePath)
  }

  def bytes(b: Boolean): Array[Byte] = Array[Byte](if (b) 1 else 0)
  def bytes(s: String): Array[Byte] = s.getBytes("UTF-8")
  def bytes(b: Seq[Array[Byte]]): Array[Byte] =
    bytes(b.length) ++ b.flatten.toArray[Byte]

  def bytes(l: Long): Array[Byte] = {
    val buffer = ByteBuffer.allocate(8)
    buffer.putLong(l)
    buffer.array
  }

  def bytes(i: Int): Array[Byte] = {
    val buffer = ByteBuffer.allocate(4)
    buffer.putInt(i)
    buffer.array
  }
}
