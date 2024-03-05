package sbt
package internal

import dotty.tools.dotc.ast
import dotty.tools.dotc.ast.{ tpd, untpd }
import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.config.ScalaSettings
import dotty.tools.dotc.core.Contexts.{ atPhase, Context }
import dotty.tools.dotc.core.{ Flags, Names, Phases, Symbols, Types }
import dotty.tools.dotc.Driver
import dotty.tools.dotc.parsing.Parsers.Parser
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.Run
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.{ PlainDirectory, Directory, VirtualDirectory, VirtualFile }
import dotty.tools.repl.AbstractFileClassLoader
import java.io.File
import java.net.URLClassLoader
import java.nio.charset.StandardCharsets
import java.nio.file.{ Files, Path, Paths, StandardOpenOption }
import java.security.MessageDigest
import scala.jdk.CollectionConverters.*
import scala.quoted.*
import sbt.io.Hash

/**
 * - nonCpOptions - non-classpath options
 * - classpath - classpath used for evaluation
 * - backingDir - directory to save `*.class` files
 * - mkReporter - an optional factory method to create a reporter
 */
class Eval(
    nonCpOptions: Seq[String],
    classpath: Seq[Path],
    backingDir: Option[Path],
    mkReporter: Option[() => Reporter]
):
  import Eval.*

  backingDir.foreach { dir =>
    Files.createDirectories(dir)
  }
  private val outputDir =
    backingDir match
      case Some(dir) => PlainDirectory(Directory(dir.toString))
      case None      => VirtualDirectory("output")
  private val classpathString = (backingDir.toList ++ classpath)
    .map(_.toString)
    .mkString(":")
  private lazy val driver: EvalDriver = new EvalDriver
  private lazy val reporter = mkReporter match
    case Some(fn) => fn()
    case None     => EvalReporter.store

  final class EvalDriver extends Driver:
    import dotty.tools.dotc.config.Settings.Setting._
    val compileCtx0 = initCtx.fresh
    val options = nonCpOptions ++ Seq("-classpath", classpathString, "dummy.scala")
    val compileCtx1 = setup(options.toArray, compileCtx0) match
      case Some((_, ctx)) => ctx
      case _              => sys.error(s"initialization failed for $options")
    val compileCtx2 = compileCtx1.fresh
      .setSetting(
        compileCtx1.settings.outputDir,
        outputDir
      )
      .setReporter(reporter)
    val compileCtx = compileCtx2
    val compiler = newCompiler(using compileCtx)
  end EvalDriver

  def eval(expression: String, tpeName: Option[String]): EvalResult =
    eval(expression, noImports, tpeName, "<setting>", Eval.DefaultStartLine)

  def evalInfer(expression: String): EvalResult =
    eval(expression, noImports, None, "<setting>", Eval.DefaultStartLine)

  def evalInfer(expression: String, imports: EvalImports): EvalResult =
    eval(expression, imports, None, "<setting>", Eval.DefaultStartLine)

  def eval(
      expression: String,
      imports: EvalImports,
      tpeName: Option[String],
      srcName: String,
      line: Int
  ): EvalResult =
    val ev = new EvalType[String]:
      override def makeSource(moduleName: String): SourceFile =
        val returnType = tpeName match
          case Some(tpe) => s": $tpe"
          case _         => ""
        val header =
          imports.strings.mkString("\n") +
            s"""
               |object $moduleName {
               |  def $WrapValName${returnType} = {""".stripMargin
        val contents = s"""$header
          |$expression
          |  }
          |}
          |""".stripMargin
        val startLine = header.linesIterator.toList.size
        EvalSourceFile(srcName, startLine, contents)

      override def extract(run: Run, unit: CompilationUnit)(using ctx: Context): String =
        atPhase(Phases.typerPhase.next) {
          (new TypeExtractor).getType(unit.tpdTree)
        }

      override def read(file: Path): String =
        String(Files.readAllBytes(file), StandardCharsets.UTF_8)

      override def write(value: String, file: Path): Unit =
        Files.write(
          file,
          value.getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING
        )

      override def extraHash: String = ""

    val inter = evalCommon[String](expression :: Nil, imports, tpeName, ev)
    val valueFn = (cl: ClassLoader) => getValue[Any](inter.enclosingModule, inter.loader(cl))
    EvalResult(
      tpe = inter.extra,
      getValue = valueFn,
      generated = inter.generated,
    )
  end eval

  def evalDefinitions(
      definitions: Seq[(String, scala.Range)],
      imports: EvalImports,
      srcName: String,
      valTypes: Seq[String],
  ): EvalDefinitions =
    evalDefinitions(definitions, imports, srcName, valTypes, "")

  def evalDefinitions(
      definitions: Seq[(String, scala.Range)],
      imports: EvalImports,
      srcName: String,
      valTypes: Seq[String],
      extraHash: String,
  ): EvalDefinitions =
    // println(s"""evalDefinitions(definitions = $definitions)
    // backingDir = $backingDir,
    // """)
    require(definitions.nonEmpty, "definitions to evaluate cannot be empty.")
    val extraHash0 = extraHash
    val ev = new EvalType[Seq[String]]:
      override def makeSource(moduleName: String): SourceFile =
        val header =
          imports.strings.mkString("\n") +
            s"""
               |object $moduleName {""".stripMargin
        val contents =
          s"""$header
          |${definitions.map(_._1).mkString("\n")}
          |}
          |""".stripMargin
        val startLine = header.linesIterator.toList.size
        EvalSourceFile(srcName, startLine, contents)

      override def extract(run: Run, unit: CompilationUnit)(using ctx: Context): Seq[String] =
        atPhase(Phases.typerPhase.next) {
          (new ValExtractor(valTypes.toSet)).getVals(unit.tpdTree)
        }(using run.runContext)

      override def read(file: Path): Seq[String] =
        new String(Files.readAllBytes(file), StandardCharsets.UTF_8).linesIterator.toList

      override def write(value: Seq[String], file: Path): Unit =
        Files.write(
          file,
          value.mkString("\n").getBytes(StandardCharsets.UTF_8),
          StandardOpenOption.CREATE,
          StandardOpenOption.TRUNCATE_EXISTING
        )

      override def extraHash: String = extraHash0

    val inter = evalCommon[Seq[String]](definitions.map(_._1), imports, tpeName = Some(""), ev)
    EvalDefinitions(inter.loader, inter.generated, inter.enclosingModule, inter.extra.reverse)

  end evalDefinitions

  private[this] def evalCommon[A](
      content: Seq[String],
      imports: EvalImports,
      tpeName: Option[String],
      ev: EvalType[A],
  ): EvalIntermediate[A] =
    import Eval.*
    // This is a hot path.
    val digester = MessageDigest.getInstance("SHA")
    content.foreach { c =>
      digester.update(bytes(c))
    }
    tpeName.foreach { tpe =>
      digester.update(bytes(tpe))
    }
    digester.update(bytes(ev.extraHash))
    val d = digester.digest()
    val hash = Hash.toHex(d)
    val moduleName = makeModuleName(hash)
    val (extra, loader) = backingDir match
      case Some(backing) if classExists(backing, moduleName) =>
        val loader = (parent: ClassLoader) =>
          (new URLClassLoader(Array(backing.toUri.toURL), parent): ClassLoader)
        val extra = ev.read(cacheFile(backing, moduleName))
        (extra, loader)
      case _ => compileAndLoad(ev, moduleName)
    val generatedFiles = getGeneratedFiles(moduleName)
    EvalIntermediate(
      extra = extra,
      loader = loader,
      generated = generatedFiles,
      enclosingModule = moduleName,
    )

  // location of the cached type or definition information
  private[this] def cacheFile(base: Path, moduleName: String): Path =
    base.resolve(moduleName + ".cache")

  private[this] def compileAndLoad[A](
      ev: EvalType[A],
      moduleName: String,
  ): (A, ClassLoader => ClassLoader) =
    given rootCtx: Context = driver.compileCtx
    val run = driver.compiler.newRun
    val source = ev.makeSource(moduleName)
    run.compileSources(source :: Nil)
    checkError("an error in expression")
    val unit = run.units.head
    val extra: A = ev.extract(run, unit)
    backingDir.foreach { backing =>
      ev.write(extra, cacheFile(backing, moduleName))
    }
    val loader = (parent: ClassLoader) => AbstractFileClassLoader(outputDir, parent)
    (extra, loader)

  private[this] final class EvalIntermediate[A](
      val extra: A,
      val loader: ClassLoader => ClassLoader,
      val generated: Seq[Path],
      val enclosingModule: String,
  )

  private[this] def classExists(dir: Path, name: String): Boolean =
    Files.exists(dir.resolve(s"$name.class"))

  private[this] def getGeneratedFiles(moduleName: String): Seq[Path] =
    backingDir match
      case Some(dir) =>
        Files
          .list(dir)
          .filter(!Files.isDirectory(_))
          .filter(_.getFileName.toString.contains(moduleName))
          .iterator
          .asScala
          .toList
      case None => Nil

  private[this] def makeModuleName(hash: String): String = "$Wrap" + hash.take(10)

  private[this] def checkError(label: String)(using ctx: Context): Unit =
    if ctx.reporter.hasErrors then
      throw new EvalException(label + ": " + ctx.reporter.allErrors.head.toString)
    else ()
end Eval

object Eval:
  private[sbt] val DefaultStartLine = 0

  lazy val noImports = EvalImports(Nil)

  def apply(): Eval =
    new Eval(Nil, currentClasspath, None, None)

  def apply(mkReporter: () => Reporter): Eval =
    new Eval(Nil, currentClasspath, None, Some(mkReporter))

  def apply(
      backingDir: Path,
      mkReporter: () => Reporter,
  ): Eval =
    new Eval(Nil, currentClasspath, Some(backingDir), Some(mkReporter))

  def apply(
      nonCpOptions: Seq[String],
      backingDir: Path,
      mkReporter: () => Reporter,
  ): Eval =
    new Eval(nonCpOptions, currentClasspath, Some(backingDir), Some(mkReporter))

  inline def apply[A](expression: String): A = ${ evalImpl[A]('{ expression }) }
  private def thisClassLoader = this.getClass.getClassLoader
  def evalImpl[A: Type](expression: Expr[String])(using qctx: Quotes): Expr[A] =
    import quotes.reflect._
    val sym = TypeRepr.of[A].typeSymbol
    val fullName = Expr(sym.fullName)
    '{
      Eval().eval($expression, Some($fullName)).getValue(thisClassLoader).asInstanceOf[A]
    }

  def currentClasspath: Seq[Path] =
    val urls = sys.props
      .get("java.class.path")
      .map(_.split(File.pathSeparator))
      .getOrElse(Array.empty[String])
    urls.toVector.map(Paths.get(_))

  def bytes(s: String): Array[Byte] = s.getBytes("UTF-8")

  /** The name of the synthetic val in the synthetic module that an expression is assigned to. */
  private[sbt] final val WrapValName = "$sbtdef"

  // used to map the position offset
  class EvalSourceFile(name: String, startLine: Int, contents: String)
      extends SourceFile(
        new VirtualFile(name, contents.getBytes(StandardCharsets.UTF_8)),
        contents.toArray
      ):
    override def lineToOffset(line: Int): Int = super.lineToOffset((line + startLine) max 0)
    override def offsetToLine(offset: Int): Int = super.offsetToLine(offset) - startLine
  end EvalSourceFile

  trait EvalType[A]:
    def makeSource(moduleName: String): SourceFile

    /** Extracts additional information after the compilation unit is evaluated. */
    def extract(run: Run, unit: CompilationUnit)(using ctx: Context): A

    /** Deserializes the extra information for unchanged inputs from a cache file. */
    def read(file: Path): A

    /**
     * Serializes the extra information to a cache file, where it can be `read` back if inputs
     * haven't changed.
     */
    def write(value: A, file: Path): Unit

    /** Extra information to include in the hash'd object name to help avoid collisions. */
    def extraHash: String
  end EvalType

  class TypeExtractor extends tpd.TreeTraverser:
    private[this] var result = ""
    def getType(t: tpd.Tree)(using ctx: Context): String =
      result = ""
      this((), t)
      result
    override def traverse(tree: tpd.Tree)(using ctx: Context): Unit =
      tree match
        case tpd.DefDef(name, _, tpt, _) if name.toString == WrapValName =>
          result = tpt.typeOpt.show
        case t: tpd.Template   => this((), t.body)
        case t: tpd.PackageDef => this((), t.stats)
        case t: tpd.TypeDef    => this((), t.rhs)
        case _                 => ()
  end TypeExtractor

  /**
   * Tree traverser that obtains the names of vals in a top-level module whose type is a subtype of
   * one of `types`.
   */
  class ValExtractor(tpes: Set[String]) extends tpd.TreeTraverser:
    private[this] var vals = List[String]()

    def getVals(t: tpd.Tree)(using ctx: Context): List[String] =
      vals = Nil
      traverse(t)
      vals

    def isAcceptableType(tpe: Types.Type)(using ctx: Context): Boolean =
      tpe.baseClasses.exists { sym =>
        tpes.contains(sym.fullName.toString)
      }

    def isTopLevelModule(sym: Symbols.Symbol)(using ctx: Context): Boolean =
      (sym is Flags.Module) && (sym.owner is Flags.ModuleClass)

    override def traverse(tree: tpd.Tree)(using ctx: Context): Unit =
      tree match
        case tpd.ValDef(name, tpt, _)
            if isTopLevelModule(tree.symbol.owner) && isAcceptableType(tpt.tpe) =>
          vals ::= name.mangledString
        case tpd.ValDef(name, tpt, _) if name.mangledString.contains("$lzy") =>
          val str = name.mangledString
          val methodName = str.take(str.indexOf("$"))
          val m = tree.symbol.owner.requiredMethod(methodName)
          if isAcceptableType(m.info) then vals ::= methodName
        case t: tpd.Template   => this((), t.body)
        case t: tpd.PackageDef => this((), t.stats)
        case t: tpd.TypeDef    => this((), t.rhs)
        case _                 => ()
  end ValExtractor

  /**
   * Gets the value of the expression wrapped in module `objectName`, which is accessible via
   * `loader`. The module name should not include the trailing `$`.
   */
  def getValue[A](objectName: String, loader: ClassLoader): A =
    val module = getModule(objectName, loader)
    val accessor = module.getClass.getMethod(WrapValName)
    val value = accessor.invoke(module)
    value.asInstanceOf[A]

  /**
   * Gets the top-level module `moduleName` from the provided class `loader`. The module name should
   * not include the trailing `$`.
   */
  def getModule(moduleName: String, loader: ClassLoader): Any =
    val clazz = Class.forName(moduleName + "$", true, loader)
    clazz.getField("MODULE$").get(null)
end Eval

final class EvalResult(
    val tpe: String,
    val getValue: ClassLoader => Any,
    val generated: Seq[Path],
)

/**
 * The result of evaluating a group of Scala definitions. The definitions are wrapped in an
 * auto-generated, top-level module named `enclosingModule`. `generated` contains the compiled
 * classes and cache files related to the definitions. A new class loader containing the module may
 * be obtained from `loader` by passing the parent class loader providing the classes from the
 * classpath that the definitions were compiled against. The list of vals with the requested types
 * is `valNames`. The values for these may be obtained by providing the parent class loader to
 * `values` as is done with `loader`.
 */
final class EvalDefinitions(
    val loader: ClassLoader => ClassLoader,
    val generated: Seq[Path],
    val enclosingModule: String,
    val valNames: Seq[String]
):
  def values(parent: ClassLoader): Seq[Any] = {
    val module = Eval.getModule(enclosingModule, loader(parent))
    for n <- valNames
    yield module.getClass.getMethod(n).invoke(module)
  }
end EvalDefinitions

final class EvalException(msg: String) extends RuntimeException(msg)

final class EvalImports(val strings: Seq[String])
