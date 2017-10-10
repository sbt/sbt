/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package compiler

import scala.collection.mutable.ListBuffer
import scala.tools.nsc.{ ast, io, reporters, CompilerCommand, Global, Phase, Settings }
import io.{ AbstractFile, PlainFile, VirtualDirectory }
import ast.parser.Tokens
import reporters.{ ConsoleReporter, Reporter }
import scala.reflect.internal.util.{ AbstractFileClassLoader, BatchSourceFile }
import Tokens.{ EOF, NEWLINE, NEWLINES, SEMI }
import java.io.File
import java.nio.ByteBuffer
import java.net.URLClassLoader
import Eval.{ getModule, getValue, WrapValName }

import sbt.io.{ DirectoryFilter, FileFilter, GlobFilter, Hash, IO, Path }

// TODO: provide a way to cleanup backing directory

final class EvalImports(val strings: Seq[(String, Int)], val srcName: String)

/**
 * The result of evaluating a Scala expression.  The inferred type of the expression is given by `tpe`.
 * The value may be obtained from `getValue` by providing a parent class loader that provides the classes from the classpath
 * this expression was compiled against.  Each call to `getValue` constructs a new class loader and loads
 * the module from that class loader.  `generated` contains the compiled classes and cache files related
 * to the expression.  The name of the auto-generated module wrapping the expression is `enclosingModule`.
 */
final class EvalResult(val tpe: String,
                       val getValue: ClassLoader => Any,
                       val generated: Seq[File],
                       val enclosingModule: String)

/**
 * The result of evaluating a group of Scala definitions.  The definitions are wrapped in an auto-generated,
 * top-level module named `enclosingModule`.  `generated` contains the compiled classes and cache files related to the definitions.
 * A new class loader containing the module may be obtained from `loader` by passing the parent class loader providing the classes
 * from the classpath that the definitions were compiled against.  The list of vals with the requested types is `valNames`.
 * The values for these may be obtained by providing the parent class loader to `values` as is done with `loader`.
 */
final class EvalDefinitions(val loader: ClassLoader => ClassLoader,
                            val generated: Seq[File],
                            val enclosingModule: String,
                            val valNames: Seq[String]) {
  def values(parent: ClassLoader): Seq[Any] = {
    val module = getModule(enclosingModule, loader(parent))
    for (n <- valNames) yield module.getClass.getMethod(n).invoke(module)
  }
}

final class EvalException(msg: String) extends RuntimeException(msg)
// not thread safe, since it reuses a Global instance
final class Eval(optionsNoncp: Seq[String],
                 classpath: Seq[File],
                 mkReporter: Settings => Reporter,
                 backing: Option[File]) {
  def this(mkReporter: Settings => Reporter, backing: Option[File]) =
    this(Nil, IO.classLocationFile[Product] :: Nil, mkReporter, backing)
  def this() = this(s => new ConsoleReporter(s), None)

  backing.foreach(IO.createDirectory)
  val classpathString = Path.makeString(classpath ++ backing.toList)
  val options = "-cp" +: classpathString +: optionsNoncp

  lazy val settings = {
    val s = new Settings(println)
    new CompilerCommand(options.toList, s) // this side-effects on Settings..
    s
  }
  lazy val reporter = mkReporter(settings)

  /**
   * Subclass of Global which allows us to mutate currentRun from outside.
   * See for rationale https://issues.scala-lang.org/browse/SI-8794
   */
  final class EvalGlobal(settings: Settings, reporter: Reporter)
      extends Global(settings, reporter) {
    override def currentRun: Run = curRun
    var curRun: Run = null
  }
  lazy val global: EvalGlobal = new EvalGlobal(settings, reporter)
  import global._

  private[sbt] def unlinkDeferred(): Unit = {
    toUnlinkLater foreach unlink
    toUnlinkLater = Nil
  }

  private[this] var toUnlinkLater = List[Symbol]()
  private[this] def unlink(sym: Symbol) = sym.owner.info.decls.unlink(sym)

  def eval(expression: String,
           imports: EvalImports = noImports,
           tpeName: Option[String] = None,
           srcName: String = "<setting>",
           line: Int = DefaultStartLine): EvalResult = {
    val ev = new EvalType[String] {
      def makeUnit = mkUnit(srcName, line, expression)
      def unlink = true
      def unitBody(unit: CompilationUnit, importTrees: Seq[Tree], moduleName: String): Tree = {
        val (parser, tree) = parse(unit, settingErrorStrings, _.expr())
        val tpt: Tree = expectedType(tpeName)
        augment(parser, importTrees, tree, tpt, moduleName)
      }
      def extra(run: Run, unit: CompilationUnit) = enteringPhase(run.typerPhase.next) {
        (new TypeExtractor).getType(unit.body)
      }
      def read(file: File) = IO.read(file)
      def write(value: String, f: File) = IO.write(f, value)
      def extraHash = ""
    }
    val i = evalCommon(expression :: Nil, imports, tpeName, ev)
    val value = (cl: ClassLoader) => getValue[Any](i.enclosingModule, i.loader(cl))
    new EvalResult(i.extra, value, i.generated, i.enclosingModule)
  }
  def evalDefinitions(definitions: Seq[(String, scala.Range)],
                      imports: EvalImports,
                      srcName: String,
                      file: Option[File],
                      valTypes: Seq[String]): EvalDefinitions = {
    require(definitions.nonEmpty, "Definitions to evaluate cannot be empty.")
    val ev = new EvalType[Seq[String]] {
      lazy val (fullUnit, defUnits) = mkDefsUnit(srcName, definitions)
      def makeUnit = fullUnit
      def unlink = false
      def unitBody(unit: CompilationUnit, importTrees: Seq[Tree], moduleName: String): Tree = {
        val fullParser = new syntaxAnalyzer.UnitParser(unit)
        val trees = defUnits flatMap parseDefinitions
        syntheticModule(fullParser, importTrees, trees.toList, moduleName)
      }
      def extra(run: Run, unit: CompilationUnit) = {
        enteringPhase(run.typerPhase.next) {
          (new ValExtractor(valTypes.toSet)).getVals(unit.body)
        }
      }
      def read(file: File) = IO.readLines(file)
      def write(value: Seq[String], file: File) = IO.writeLines(file, value)
      def extraHash = file match {
        case Some(f) => f.getAbsolutePath
        case None    => ""
      }
    }
    val i = evalCommon(definitions.map(_._1), imports, Some(""), ev)
    new EvalDefinitions(i.loader, i.generated, i.enclosingModule, i.extra)
  }

  private[this] def evalCommon[T](content: Seq[String],
                                  imports: EvalImports,
                                  tpeName: Option[String],
                                  ev: EvalType[T]): EvalIntermediate[T] = {
    import Eval._
    // TODO - We also encode the source of the setting into the hash to avoid conflicts where the exact SAME setting
    // is defined in multiple evaluated instances with a backing.  This leads to issues with finding a previous
    // value on the classpath when compiling.
    val hash = Hash.toHex(
      Hash(bytes(
        stringSeqBytes(content) :: optBytes(backing)(fileExistsBytes) :: stringSeqBytes(options) ::
          seqBytes(classpath)(fileModifiedBytes) :: stringSeqBytes(imports.strings.map(_._1)) :: optBytes(
          tpeName)(bytes) ::
          bytes(ev.extraHash) :: Nil)))
    val moduleName = makeModuleName(hash)

    lazy val unit = {
      reporter.reset
      ev.makeUnit
    }
    lazy val run = new Run {
      override def units = (unit :: Nil).iterator
    }
    def unlinkAll(): Unit =
      for ((sym, _) <- run.symSource) if (ev.unlink) unlink(sym) else toUnlinkLater ::= sym

    val (extra, loader) = backing match {
      case Some(back) if classExists(back, moduleName) =>
        val loader = (parent: ClassLoader) => new URLClassLoader(Array(back.toURI.toURL), parent)
        val extra = ev.read(cacheFile(back, moduleName))
        (extra, loader)
      case _ =>
        try { compileAndLoad(run, unit, imports, backing, moduleName, ev) } finally { unlinkAll() }
    }

    val generatedFiles = getGeneratedFiles(backing, moduleName)
    new EvalIntermediate(extra, loader, generatedFiles, moduleName)
  }
  // location of the cached type or definition information
  private[this] def cacheFile(base: File, moduleName: String): File =
    new File(base, moduleName + ".cache")
  private[this] def compileAndLoad[T](run: Run,
                                      unit: CompilationUnit,
                                      imports: EvalImports,
                                      backing: Option[File],
                                      moduleName: String,
                                      ev: EvalType[T]): (T, ClassLoader => ClassLoader) = {
    global.curRun = run
    run.currentUnit = unit
    val dir = outputDirectory(backing)
    settings.outputDirs setSingleOutput dir

    val importTrees = parseImports(imports)
    unit.body = ev.unitBody(unit, importTrees, moduleName)

    def compile(phase: Phase): Unit = {
      globalPhase = phase
      if (phase == null || phase == phase.next || reporter.hasErrors)
        ()
      else {
        enteringPhase(phase) { phase.run }
        compile(phase.next)
      }
    }

    compile(run.namerPhase)
    checkError("Type error in expression")

    val extra = ev.extra(run, unit)
    for (f <- backing) ev.write(extra, cacheFile(f, moduleName))
    val loader = (parent: ClassLoader) => new AbstractFileClassLoader(dir, parent)
    (extra, loader)
  }

  private[this] def expectedType(tpeName: Option[String]): Tree =
    tpeName match {
      case Some(tpe) => parseType(tpe)
      case None      => TypeTree(NoType)
    }

  private[this] def outputDirectory(backing: Option[File]): AbstractFile =
    backing match {
      case None => new VirtualDirectory("<virtual>", None); case Some(dir) => new PlainFile(dir)
    }

  def load(dir: AbstractFile, moduleName: String): ClassLoader => Any =
    parent => getValue[Any](moduleName, new AbstractFileClassLoader(dir, parent))
  def loadPlain(dir: File, moduleName: String): ClassLoader => Any =
    parent => getValue[Any](moduleName, new URLClassLoader(Array(dir.toURI.toURL), parent))

  //wrap tree in object objectName { def WrapValName = <tree> }
  def augment(parser: global.syntaxAnalyzer.UnitParser,
              imports: Seq[Tree],
              tree: Tree,
              tpt: Tree,
              objectName: String): Tree = {
    val method = DefDef(NoMods, newTermName(WrapValName), Nil, Nil, tpt, tree)
    syntheticModule(parser, imports, method :: Nil, objectName)
  }
  private[this] def syntheticModule(parser: global.syntaxAnalyzer.UnitParser,
                                    imports: Seq[Tree],
                                    definitions: List[Tree],
                                    objectName: String): Tree = {
    val emptyTypeName = nme.EMPTY.toTypeName
    def emptyPkg = parser.atPos(0, 0, 0) { Ident(nme.EMPTY_PACKAGE_NAME) }
    def emptyInit = DefDef(
      NoMods,
      nme.CONSTRUCTOR,
      Nil,
      List(Nil),
      TypeTree(),
      Block(List(Apply(Select(Super(This(emptyTypeName), emptyTypeName), nme.CONSTRUCTOR), Nil)),
            Literal(Constant(())))
    )

    def moduleBody = Template(List(gen.scalaAnyRefConstr), noSelfType, emptyInit :: definitions)
    def moduleDef = ModuleDef(NoMods, newTermName(objectName), moduleBody)
    parser.makePackaging(0, emptyPkg, (imports :+ moduleDef).toList)
  }

  private[this] final class TypeExtractor extends Traverser {
    private[this] var result = ""
    def getType(t: Tree) = { result = ""; traverse(t); result }
    override def traverse(tree: Tree): Unit = tree match {
      case d: DefDef if d.symbol.nameString == WrapValName =>
        result = d.symbol.tpe.finalResultType.toString
      case _ => super.traverse(tree)
    }
  }

  /** Tree traverser that obtains the names of vals in a top-level module whose type is a subtype of one of `types`.*/
  private[this] final class ValExtractor(tpes: Set[String]) extends Traverser {
    private[this] var vals = List[String]()
    def getVals(t: Tree): List[String] = { vals = Nil; traverse(t); vals }
    def isAcceptableType(tpe: Type): Boolean = {
      tpe.baseClasses.exists { sym =>
        tpes.contains(sym.fullName)
      }
    }
    override def traverse(tree: Tree): Unit = tree match {
      case ValDef(_, n, actualTpe, _)
          if isTopLevelModule(tree.symbol.owner) && isAcceptableType(actualTpe.tpe) =>
        vals ::= n.dropLocal.encoded
      case _ => super.traverse(tree)
    }
  }
  // inlined implemented of Symbol.isTopLevelModule that was removed in e5b050814deb2e7e1d6d05511d3a6cb6b013b549
  private[this] def isTopLevelModule(s: Symbol): Boolean =
    s.hasFlag(reflect.internal.Flags.MODULE) && s.owner.isPackageClass

  private[this] final class EvalIntermediate[T](val extra: T,
                                                val loader: ClassLoader => ClassLoader,
                                                val generated: Seq[File],
                                                val enclosingModule: String)

  private[this] def classExists(dir: File, name: String) = (new File(dir, name + ".class")).exists
  // TODO: use the code from Analyzer
  private[this] def getGeneratedFiles(backing: Option[File], moduleName: String): Seq[File] =
    backing match {
      case None      => Nil
      case Some(dir) => dir listFiles moduleFileFilter(moduleName)
    }
  private[this] def moduleFileFilter(moduleName: String) = new java.io.FilenameFilter {
    def accept(dir: File, s: String) =
      (s contains moduleName)
  }

  private[this] class ParseErrorStrings(val base: String,
                                        val extraBlank: String,
                                        val missingBlank: String,
                                        val extraSemi: String)
  private[this] def definitionErrorStrings = new ParseErrorStrings(
    base = "Error parsing definition.",
    extraBlank = "  Ensure that there are no blank lines within a definition.",
    missingBlank = "  Ensure that definitions are separated by blank lines.",
    extraSemi = "  A trailing semicolon is not permitted for standalone definitions."
  )
  private[this] def settingErrorStrings = new ParseErrorStrings(
    base = "Error parsing expression.",
    extraBlank = "  Ensure that there are no blank lines within a setting.",
    missingBlank = "  Ensure that settings are separated by blank lines.",
    extraSemi =
      "  Note that settings are expressions and do not end with semicolons.  (Semicolons are fine within {} blocks, however.)"
  )

  /**
   * Parses the provided compilation `unit` according to `f` and then performs checks on the final parser state
   * to catch errors that are common when the content is embedded in a blank-line-delimited format.
   */
  private[this] def parse[T](unit: CompilationUnit,
                             errors: ParseErrorStrings,
                             f: syntaxAnalyzer.UnitParser => T): (syntaxAnalyzer.UnitParser, T) = {
    val parser = new syntaxAnalyzer.UnitParser(unit)

    val tree = f(parser)
    val extra = parser.in.token match {
      case EOF => errors.extraBlank
      case _   => ""
    }
    checkError(errors.base + extra)

    parser.accept(EOF)
    val extra2 = parser.in.token match {
      case SEMI               => errors.extraSemi
      case NEWLINE | NEWLINES => errors.missingBlank
      case _                  => ""
    }
    checkError(errors.base + extra2)

    (parser, tree)
  }
  private[this] def parseType(tpe: String): Tree = {
    val tpeParser = new syntaxAnalyzer.UnitParser(mkUnit("<expected-type>", DefaultStartLine, tpe))
    val tpt0: Tree = tpeParser.typ()
    tpeParser.accept(EOF)
    checkError("Error parsing expression type.")
    tpt0
  }
  private[this] def parseImports(imports: EvalImports): Seq[Tree] =
    imports.strings flatMap { case (s, line) => parseImport(mkUnit(imports.srcName, line, s)) }
  private[this] def parseImport(importUnit: CompilationUnit): Seq[Tree] = {
    val parser = new syntaxAnalyzer.UnitParser(importUnit)
    val trees: Seq[Tree] = parser.importClause()
    parser.accept(EOF)
    checkError("Error parsing imports for expression.")
    trees
  }
  private[this] def parseDefinitions(du: CompilationUnit): Seq[Tree] =
    parse(du, definitionErrorStrings, parseDefinitions)._2

  /** Parses one or more definitions (defs, vals, lazy vals, classes, traits, modules). */
  private[this] def parseDefinitions(parser: syntaxAnalyzer.UnitParser): Seq[Tree] = {
    val defs = ListBuffer[Tree]()
    do {
      defs ++= parser.nonLocalDefOrDcl
      parser.acceptStatSepOpt()
    } while (!parser.isStatSeqEnd)
    defs.toList
  }

  private[this] trait EvalType[T] {

    /** Extracts additional information after the compilation unit is evaluated.*/
    def extra(run: Run, unit: CompilationUnit): T

    /** Deserializes the extra information for unchanged inputs from a cache file.*/
    def read(file: File): T

    /** Serializes the extra information to a cache file, where it can be `read` back if inputs haven't changed.*/
    def write(value: T, file: File): Unit

    /**
     * Constructs the full compilation unit for this evaluation.
     * This is used for error reporting during compilation.
     * The `unitBody` method actually does the parsing and may parse the Tree from another source.
     */
    def makeUnit: CompilationUnit

    /** If true, all top-level symbols from this evaluation will be unlinked.*/
    def unlink: Boolean

    /**
     * Constructs the Tree to be compiled.  The full compilation `unit` from `makeUnit` is provided along with the
     * parsed imports `importTrees` to be used.  `moduleName` should be name of the enclosing module.
     * The Tree doesn't need to be parsed from the contents of `unit`.
     */
    def unitBody(unit: CompilationUnit, importTrees: Seq[Tree], moduleName: String): Tree

    /** Extra information to include in the hash'd object name to help avoid collisions. */
    def extraHash: String
  }

  val DefaultStartLine = 0
  private[this] def makeModuleName(hash: String): String = "$" + Hash.halve(hash)
  private[this] def noImports = new EvalImports(Nil, "")
  private[this] def mkUnit(srcName: String, firstLine: Int, s: String) =
    new CompilationUnit(new EvalSourceFile(srcName, firstLine, s))
  private[this] def checkError(label: String) =
    if (reporter.hasErrors) throw new EvalException(label)

  private[this] final class EvalSourceFile(name: String, startLine: Int, contents: String)
      extends BatchSourceFile(name, contents) {
    override def lineToOffset(line: Int): Int = super.lineToOffset((line - startLine) max 0)
    override def offsetToLine(offset: Int): Int = super.offsetToLine(offset) + startLine
  }

  /**
   * Constructs a CompilationUnit for each definition, which can be used to independently parse the definition into a Tree.
   * Additionally, a CompilationUnit for the combined definitions is constructed for use by combined compilation after parsing.
   */
  private[this] def mkDefsUnit(
      srcName: String,
      definitions: Seq[(String, scala.Range)]): (CompilationUnit, Seq[CompilationUnit]) = {
    def fragmentUnit(content: String, lineMap: Array[Int]) =
      new CompilationUnit(fragmentSourceFile(srcName, content, lineMap))

    import collection.mutable.ListBuffer
    val lines = new ListBuffer[Int]()
    val defs = new ListBuffer[CompilationUnit]()
    val fullContent = new java.lang.StringBuilder()
    for ((defString, range) <- definitions) {
      defs += fragmentUnit(defString, range.toArray)
      fullContent.append(defString)
      lines ++= range
      fullContent.append("\n\n")
      lines ++= (range.end :: range.end :: Nil)
    }
    val fullUnit = fragmentUnit(fullContent.toString, lines.toArray)
    (fullUnit, defs.toSeq)
  }

  /**
   * Source file that can map the offset in the file to and from line numbers that may discontinuous.
   * The values in `lineMap` must be ordered, but need not be consecutive.
   */
  private[this] def fragmentSourceFile(srcName: String, content: String, lineMap: Array[Int]) =
    new BatchSourceFile(srcName, content) {
      override def lineToOffset(line: Int): Int =
        super.lineToOffset(lineMap.indexWhere(_ == line) max 0)
      override def offsetToLine(offset: Int): Int = index(lineMap, super.offsetToLine(offset))
      // the SourceFile attribute is populated from this method, so we are required to only return the name
      override def toString = new File(srcName).getName
      private[this] def index(a: Array[Int], i: Int): Int = if (i < 0 || i >= a.length) 0 else a(i)
    }
}
private[sbt] object Eval {
  def optBytes[T](o: Option[T])(f: T => Array[Byte]): Array[Byte] = seqBytes(o.toSeq)(f)
  def stringSeqBytes(s: Seq[String]): Array[Byte] = seqBytes(s)(bytes)
  def seqBytes[T](s: Seq[T])(f: T => Array[Byte]): Array[Byte] = bytes(s map f)
  def bytes(b: Seq[Array[Byte]]): Array[Byte] = bytes(b.length) ++ b.flatten.toArray[Byte]
  def bytes(b: Boolean): Array[Byte] = Array[Byte](if (b) 1 else 0)
  def filesModifiedBytes(fs: Array[File]): Array[Byte] =
    if (fs eq null) filesModifiedBytes(Array[File]()) else seqBytes(fs)(fileModifiedBytes)
  def fileModifiedBytes(f: File): Array[Byte] =
    (if (f.isDirectory) filesModifiedBytes(f listFiles classDirFilter) else bytes(f.lastModified)) ++
      bytes(f.getAbsolutePath)
  def fileExistsBytes(f: File): Array[Byte] =
    bytes(f.exists) ++
      bytes(f.getAbsolutePath)

  def bytes(s: String): Array[Byte] = s getBytes "UTF-8"
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

  /** The name of the synthetic val in the synthetic module that an expression is assigned to. */
  final val WrapValName = "$sbtdef"

  /**
   * Gets the value of the expression wrapped in module `objectName`, which is accessible via `loader`.
   * The module name should not include the trailing `$`.
   */
  def getValue[T](objectName: String, loader: ClassLoader): T = {
    val module = getModule(objectName, loader)
    val accessor = module.getClass.getMethod(WrapValName)
    val value = accessor.invoke(module)
    value.asInstanceOf[T]
  }

  /** Gets the top-level module `moduleName` from the provided class `loader`.  The module name should not include the trailing `$`.*/
  def getModule(moduleName: String, loader: ClassLoader): Any = {
    val clazz = Class.forName(moduleName + "$", true, loader)
    clazz.getField("MODULE$").get(null)
  }

  private val classDirFilter: FileFilter = DirectoryFilter || GlobFilter("*.class")
}
