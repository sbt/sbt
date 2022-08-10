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
import scala.compat.Platform.EOL
import dotty.tools.dotc.ast.Trees.Lazy
import dotty.tools.dotc.ast.untpd
import dotty.tools.dotc.ast.untpd.Tree
import dotty.tools.dotc.CompilationUnit
import dotty.tools.dotc.core.Contexts.Context
import dotty.tools.dotc.Driver
import dotty.tools.dotc.util.NoSourcePosition
import dotty.tools.dotc.util.SourceFile
import dotty.tools.dotc.util.SourcePosition
import dotty.tools.io.VirtualDirectory
import dotty.tools.io.VirtualFile
import dotty.tools.dotc.parsing.*
import dotty.tools.dotc.reporting.ConsoleReporter
import dotty.tools.dotc.reporting.Diagnostic
import dotty.tools.dotc.reporting.Reporter
import dotty.tools.dotc.reporting.StoreReporter
import scala.util.Random
import scala.util.{ Failure, Success }
import xsbti.VirtualFileRef
import dotty.tools.dotc.printing.Printer
import dotty.tools.dotc.config.Printers

private[sbt] object SbtParser:
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
  private[parser] val NOT_FOUND_INDEX = -1
  private[sbt] val FAKE_FILE = VirtualFileRef.of("fake") // new File("fake")
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
    sbt.io.Path.makeString(sbt.io.IO.classLocationPath[Product].toFile :: Nil)

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
      val sourcePath = dia.position.asScala.getOrElse(sys.error("missing position")).source.path
      val reporter = getReporter(sourcePath)
      reporter.doReport(dia)
    override def report(dia: Diagnostic)(using Context): Unit =
      import scala.jdk.OptionConverters.*
      val sourcePath = dia.position.asScala.getOrElse(sys.error("missing position")).source.path
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
        val errorMessage = seq.mkString(EOL)
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
    import dotty.tools.dotc.config.Settings.Setting._
    val compileCtx0 = initCtx.fresh
    val options = List("-classpath", s"$defaultClasspath", "dummy.scala")
    val compileCtx1 = setup(options.toArray, compileCtx0) match
      case Some((_, ctx)) => ctx
      case _              => sys.error(s"initialization failed for $options")
    val outputDir = VirtualDirectory("output")
    val compileCtx2 = compileCtx1.fresh
      .setSetting(
        compileCtx1.settings.outputDir,
        outputDir
      )
      .setReporter(globalReporter)
    val compileCtx = compileCtx2
    val compiler = newCompiler(using compileCtx)
  end ParseDriver

  /**
   * Parse code reusing the same [[Run]] instance.
   *
   * @param code The code to be parsed.
   * @param filePath The file name where the code comes from.
   * @param reporterId0 The reporter id is the key used to get the pertinent
   *                    reporter. Given that the parsing reuses a global
   *                    instance, this reporter id makes sure that every parsing
   *                    session gets its own errors in a concurrent setting.
   *                    The reporter id must be unique per parsing session.
   * @return
   */
  private[sbt] def parse(
      code: String,
      filePath: String,
      reporterId0: Option[String]
  ): (List[untpd.Tree], String, SourceFile) =
    import defaultGlobalForParser.*
    given ctx: Context = compileCtx
    val reporterId = reporterId0.getOrElse(s"$filePath-${Random.nextInt}")
    val reporter = globalReporter.getOrCreateReporter(reporterId)
    reporter.removeBufferedMessages
    val moduleName = "SyntheticModule"
    val wrapCode = s"""object $moduleName {
                      |$code
                      |}""".stripMargin
    val wrapperFile = SourceFile(
      VirtualFile(reporterId, wrapCode.getBytes(StandardCharsets.UTF_8)),
      scala.io.Codec.UTF8
    )
    val parser = Parsers.Parser(wrapperFile)
    val t = parser.parse()
    val parsedTrees = t match
      case untpd.PackageDef(_, List(untpd.ModuleDef(_, untpd.Template(_, _, _, trees)))) =>
        trees match
          case ts: List[untpd.Tree]       => ts
          case ts: Lazy[List[untpd.Tree]] => ts.complete
    globalReporter.throwParserErrorsIfAny(reporter, filePath)
    (parsedTrees, reporterId, wrapperFile)
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
    val indexedLines = lines.toIndexedSeq
    val content = indexedLines.mkString(END_OF_LINE)
    val fileName = path.id
    val (parsedTrees, reporterId, sourceFile) = parse(content, fileName, None)
    given ctx: Context = compileCtx

    val (imports: Seq[untpd.Tree], statements: Seq[untpd.Tree]) =
      parsedTrees.partition {
        case _: untpd.Import => true
        case _               => false
      }

    def convertStatement(tree: untpd.Tree)(using ctx: Context): Option[(String, Tree, LineRange)] =
      if tree.span.exists then
        // not sure why I need to reconstruct the position myself
        val pos = SourcePosition(sourceFile, tree.span)
        val statement = String(pos.linesSlice).trim()
        val lines = pos.lines
        val wrapperLineOffset = 0
        Some(
          (
            statement,
            tree,
            LineRange(lines.start + wrapperLineOffset, lines.end + wrapperLineOffset)
          )
        )
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
  )(using context: Context): Seq[(String, Int)] =
    imports.map { tree =>
      // not sure why I need to reconstruct the position myself
      val pos = SourcePosition(sourceFile, tree.span)
      val content = String(pos.linesSlice).trim()
      val wrapperLineOffset = 0
      (content, pos.line + wrapperLineOffset)
    }
end SbtParser
