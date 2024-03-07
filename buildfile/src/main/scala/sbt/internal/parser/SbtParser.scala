/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.util.{ LineRange, MessageOnlyException }
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import sbt.internal.parser.SbtParser._
import dotty.tools.dotc.ast.Trees.Lazy
import dotty.tools.dotc.ast.untpd.*
import dotty.tools.dotc.core.Contexts.*
import dotty.tools.dotc.Driver
import dotty.tools.dotc.util.SourceFile
import dotty.tools.io.VirtualDirectory
import dotty.tools.io.VirtualFile
import dotty.tools.dotc.parsing.*
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.Diagnostic
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.StoreReporter
import scala.util.Random
import xsbti.VirtualFileRef

private[sbt] object SbtParser:
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
  val WRAPPER_POSITION_OFFSET = 25 // size of the wrapper object
  private[parser] val NOT_FOUND_INDEX = -1
  private[sbt] val FAKE_FILE = VirtualFileRef.of("fake")
  private[parser] val XML_ERROR = "';' expected but 'val' found."

  private val XmlErrorMessage =
    """Probably problem with parsing xml group, please add parens or semicolons:
      |Replace:
      |val xmlGroup = <a/><b/>
      |with:
      |val xmlGroup = (<a/><b/>)
      |or
      |val xmlGroup = <a/><b/>;
    """.stripMargin

  private final val defaultClasspath =
    sbt.io.Path.makeString(sbt.io.IO.classLocationPath(classOf[Product]).toFile :: Nil)

  def isIdentifier(ident: String): Boolean =
    val code = s"val $ident = 0; val ${ident}${ident} = $ident"
    try
      val p = SbtParser(FAKE_FILE, List(code))
      true
    catch case e: Throwable => false

  /**
   * Provides the previous error reporting functionality in
   * [[scala.tools.reflect.ToolBox]].
   *
   * This parser is a wrapper around a collection of reporters that are
   * indexed by a unique key. This is used to ensure that the reports of
   * one parser don't collide with other ones in concurrent settings.
   *
   * This parser is a sign that this whole parser should be rewritten.
   * There are exceptions everywhere and the logic to work around
   * the scalac parser bug heavily relies on them and it's tied
   * to the test suite. Ideally, we only want to throw exceptions
   * when we know for a fact that the user-provided snippet doesn't
   * parse.
   */
  private[sbt] class UniqueParserReporter extends Reporter {

    private val reporters = new ConcurrentHashMap[String, StoreReporter]()

    override def doReport(dia: Diagnostic)(using Context): Unit =
      import scala.jdk.OptionConverters.*
      val sourcePath = dia.position.toScala.getOrElse(sys.error("missing position")).source.path
      val reporter = getReporter(sourcePath)
      reporter.doReport(dia)
    override def report(dia: Diagnostic)(using Context): Unit =
      import scala.jdk.OptionConverters.*
      val sourcePath = dia.position.toScala.getOrElse(sys.error("missing position")).source.path
      val reporter = getReporter(sourcePath)
      reporter.report(dia)

    override def hasErrors: Boolean = {
      var result = false
      reporters.forEachValue(100, r => if (r.hasErrors) result = true)
      result
    }

    def createReporter(uniqueFileName: String): StoreReporter =
      val r = new StoreReporter(null)
      reporters.put(uniqueFileName, r)
      r

    def getOrCreateReporter(uniqueFileName: String): StoreReporter = {
      val r = reporters.get(uniqueFileName)
      if (r == null) createReporter(uniqueFileName)
      else r
    }

    private def getReporter(fileName: String) = {
      val reporter = reporters.get(fileName)
      if (reporter == null) {
        scalacGlobalInitReporter.getOrElse(
          sys.error(s"sbt forgot to initialize `scalacGlobalInitReporter`.")
        )
      } else reporter
    }

    def throwParserErrorsIfAny(reporter: StoreReporter, fileName: String)(using
        context: Context
    ): Unit =
      if reporter.hasErrors then {
        val seq = reporter.pendingMessages.map { info =>
          s"""[$fileName]:${info.pos.line}: ${info.msg}"""
        }
        val errorMessage = seq.mkString(System.lineSeparator)
        val error: String =
          if (errorMessage.contains(XML_ERROR))
            s"$errorMessage\n${SbtParser.XmlErrorMessage}"
          else errorMessage
        throw new MessageOnlyException(error)
      } else ()
  }

  private[sbt] var scalacGlobalInitReporter: Option[ConsoleReporter] = None

  private[sbt] val globalReporter = UniqueParserReporter()
  private[sbt] val defaultGlobalForParser = ParseDriver()
  private[sbt] final class ParseDriver extends Driver:
    override protected val sourcesRequired: Boolean = false
    val compileCtx0 = initCtx.fresh
    val options = List("-classpath", s"$defaultClasspath")
    val compileCtx1 = setup(options.toArray, compileCtx0) match
      case Some((_, ctx)) => ctx
      case _              => sys.error(s"initialization failed for $options")
    val compileCtx: Context = compileCtx1.fresh
      .setSetting(compileCtx1.settings.outputDir, VirtualDirectory("output"))
      .setReporter(globalReporter)
    val compiler = newCompiler(using compileCtx)
  end ParseDriver

  /**
   * Parse code reusing the same [[Run]] instance.
   *
   * @param filePath The file name where the code comes from.
   * @param reporterId The reporter id is the key used to get the pertinent
   *                    reporter. Given that the parsing reuses a global
   *                    instance, this reporter id makes sure that every parsing
   *                    session gets its own errors in a concurrent setting.
   *                    The reporter id must be unique per parsing session.
   * @return the parsed trees
   */
  private def parse(filePath: String, reporterId: String)(using Context): List[Tree] =
    val reporter = globalReporter.getOrCreateReporter(reporterId)
    reporter.removeBufferedMessages
    val parser = Parsers.Parser(ctx.source)
    val t = parser.parse()
    val parsedTrees = t match
      case PackageDef(_, List(ModuleDef(_, Template(_, _, _, trees)))) =>
        trees match
          case ts: List[Tree]       => ts
          case ts: Lazy[List[Tree]] => ts.complete
    globalReporter.throwParserErrorsIfAny(reporter, filePath)
    parsedTrees
end SbtParser

private class SbtParserInit {
  new Thread("sbt-parser-init-thread") {
    setDaemon(true)
    start()
    override def run(): Unit = {
      val _ = SbtParser.defaultGlobalForParser
    }
  }
}

/**
 * This method solely exists to add scaladoc to members in SbtParser which
 * are defined using pattern matching.
 */
sealed trait ParsedSbtFileExpressions:
  /** The set of parsed import expressions. */
  def imports: Seq[(String, Int)]

  /** The set of parsed definitions and/or sbt build settings. */
  def settings: Seq[(String, LineRange)]

  /** The set of scala tree's for parsed definitions/settings and the underlying string representation.. */
  def settingsTrees: Seq[(String, Tree)]
end ParsedSbtFileExpressions

/**
 * An initial parser/splitter of .sbt files.
 *
 * This class is responsible for chunking a `.sbt` file into expression ranges
 * which we can then compile using the Scala compiler.
 *
 * Example:
 *
 * {{{
 *   val parser = SbtParser(myFile, IO.readLines(myFile))
 *   // All import statements
 *   val imports = parser.imports
 *   // All other statements (val x =, or raw settings)
 *   val settings = parser.settings
 * }}}
 *
 * @param file  The file we're parsing (may be a dummy file)
 * @param lines The parsed "lines" of the file, where each string is a line.
 */
private[sbt] case class SbtParser(path: VirtualFileRef, lines: Seq[String])
    extends ParsedSbtFileExpressions:
  // settingsTrees,modifiedContent needed for "session save"
  // TODO - We should look into splitting out "definitions" vs. "settings" here instead of further string lookups, since we have the
  // parsed trees.
  val (imports, settings, settingsTrees) = splitExpressions(path, lines)

  import SbtParser.defaultGlobalForParser.*

  private def splitExpressions(
      path: VirtualFileRef,
      lines: Seq[String]
  ): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)]) = {
    // import sbt.internal.parser.MissingBracketHandler.findMissingText
    val code = lines.toIndexedSeq.mkString(END_OF_LINE)
    val wrapCode = s"""object SyntheticModule {
                      |$code
                      |}""".stripMargin
    val fileName = path.id
    val reporterId = s"$fileName-${Random.nextInt}"
    val sourceFile = SourceFile(
      VirtualFile(reporterId, wrapCode.getBytes(StandardCharsets.UTF_8)),
      scala.io.Codec.UTF8
    )
    given Context = compileCtx.fresh.setSource(sourceFile)
    val parsedTrees = parse(fileName, reporterId)

    // Check No val (a,b) = foo *or* val a,b = foo as these are problematic to range positions and the WHOLE architecture.
    parsedTrees.withFilter(_.isInstanceOf[PatDef]).foreach { badTree =>
      throw new MessageOnlyException(
        s"[${fileName}]:${badTree.line}: Pattern matching in val statements is not supported"
      )
    }

    val (imports: Seq[Tree], statements: Seq[Tree]) =
      parsedTrees.partition {
        case _: Import => true
        case _         => false
      }

    def convertStatement(tree: Tree)(using Context): Option[(String, Tree, LineRange)] =
      if tree.span.exists then
        val pos = tree.sourcePos
        val statement = String(pos.source.content.slice(pos.start, pos.end)).trim
        Some((statement, tree, LineRange(pos.lines.start, pos.lines.end)))
      else None
    val stmtTreeLineRange = statements.flatMap(convertStatement)
    val importsLineRange = importsToLineRanges(sourceFile, imports)
    (
      importsLineRange,
      stmtTreeLineRange.map { case (stmt, _, lr) =>
        (stmt, lr)
      },
      stmtTreeLineRange.map { case (stmt, tree, _) =>
        (stmt, tree)
      }
    )
  }

  private def importsToLineRanges(
      sourceFile: SourceFile,
      imports: Seq[Tree]
  )(using Context): Seq[(String, Int)] =
    imports.map { tree =>
      val pos = tree.sourcePos
      val content = String(pos.source.content.slice(pos.start, pos.end)).trim
      (content, tree.sourcePos.line)
    }
end SbtParser
