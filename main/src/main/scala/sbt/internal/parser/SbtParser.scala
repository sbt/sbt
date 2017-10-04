/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.util.{ LineRange, MessageOnlyException }
import java.io.File
import java.util.concurrent.ConcurrentHashMap

import sbt.internal.parser.SbtParser._

import scala.compat.Platform.EOL
import scala.reflect.internal.util.{ BatchSourceFile, Position }
import scala.reflect.io.VirtualDirectory
import scala.reflect.internal.Positions
import scala.tools.nsc.{ CompilerCommand, Global }
import scala.tools.nsc.reporters.{ ConsoleReporter, Reporter, StoreReporter }
import scala.util.Random
import scala.util.{ Failure, Success }

private[sbt] object SbtParser {
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
  private[parser] val NOT_FOUND_INDEX = -1
  private[sbt] val FAKE_FILE = new File("fake")
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
    sbt.io.Path.makeString(sbt.io.IO.classLocationFile[Product] :: Nil)

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

    override def info0(pos: Position, msg: String, severity: Severity, force: Boolean): Unit = {
      val reporter = getReporter(pos.source.file.name)
      severity.id match {
        case 0 => reporter.info(pos, msg, force)
        case 1 => reporter.warning(pos, msg)
        case 2 => reporter.error(pos, msg)
      }
    }

    def getOrCreateReporter(uniqueFileName: String): StoreReporter = {
      val reporter = reporters.get(uniqueFileName)
      if (reporter == null) {
        val newReporter = new StoreReporter
        reporters.put(uniqueFileName, newReporter)
        newReporter
      } else reporter
    }

    private def getReporter(fileName: String) = {
      val reporter = reporters.get(fileName)
      if (reporter == null) {
        scalacGlobalInitReporter.getOrElse(
          sys.error(s"Sbt forgot to initialize `scalacGlobalInitReporter`."))
      } else reporter
    }

    def throwParserErrorsIfAny(reporter: StoreReporter, fileName: String): Unit = {
      if (reporter.hasErrors) {
        val seq = reporter.infos.map { info =>
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
  }

  private[sbt] final val globalReporter = new UniqueParserReporter
  private[sbt] var scalacGlobalInitReporter: Option[ConsoleReporter] = None

  private[sbt] final lazy val defaultGlobalForParser = {
    val options = "-cp" :: s"$defaultClasspath" :: "-Yrangepos" :: Nil
    val reportError = (msg: String) => System.err.println(msg)
    val command = new CompilerCommand(options, reportError)
    val settings = command.settings
    settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))
    scalacGlobalInitReporter = Some(new ConsoleReporter(settings))

    // Mix Positions, otherwise global ignores -Yrangepos
    val global = new Global(settings, globalReporter) with Positions
    val run = new global.Run
    // Add required dummy unit for initialization...
    val initFile = new BatchSourceFile("<wrapper-init>", "")
    val _ = new global.CompilationUnit(initFile)
    global.phase = run.parserPhase
    global
  }

  import defaultGlobalForParser.Tree

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
  private[sbt] def parse(code: String,
                         filePath: String,
                         reporterId0: Option[String]): (Seq[Tree], String) = {
    import defaultGlobalForParser._
    val reporterId = reporterId0.getOrElse(s"$filePath-${Random.nextInt}")
    val reporter = globalReporter.getOrCreateReporter(reporterId)
    reporter.reset()
    val wrapperFile = new BatchSourceFile(reporterId, code)
    val unit = new CompilationUnit(wrapperFile)
    val parser = new syntaxAnalyzer.UnitParser(unit)
    val parsedTrees = parser.templateStats()
    parser.accept(scala.tools.nsc.ast.parser.Tokens.EOF)
    globalReporter.throwParserErrorsIfAny(reporter, filePath)
    parsedTrees -> reporterId
  }
}

/**
 * This method solely exists to add scaladoc to members in SbtParser which
 * are defined using pattern matching.
 */
sealed trait ParsedSbtFileExpressions {

  /** The set of parsed import expressions. */
  def imports: Seq[(String, Int)]

  /** The set of parsed definitions and/or sbt build settings. */
  def settings: Seq[(String, LineRange)]

  /** The set of scala tree's for parsed definitions/settings and the underlying string representation.. */
  def settingsTrees: Seq[(String, Global#Tree)]

}

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
private[sbt] case class SbtParser(file: File, lines: Seq[String]) extends ParsedSbtFileExpressions {
  //settingsTrees,modifiedContent needed for "session save"
  // TODO - We should look into splitting out "definitions" vs. "settings" here instead of further string lookups, since we have the
  // parsed trees.
  val (imports, settings, settingsTrees) = splitExpressions(file, lines)

  import SbtParser.defaultGlobalForParser._

  private def splitExpressions(
      file: File,
      lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)]) = {
    import sbt.internal.parser.MissingBracketHandler.findMissingText

    val indexedLines = lines.toIndexedSeq
    val content = indexedLines.mkString(END_OF_LINE)
    val fileName = file.getAbsolutePath
    val (parsedTrees, reporterId) = parse(content, fileName, None)

    // Check No val (a,b) = foo *or* val a,b = foo as these are problematic to range positions and the WHOLE architecture.
    def isBadValDef(t: Tree): Boolean =
      t match {
        case x @ ValDef(_, _, _, rhs) if rhs != EmptyTree =>
          val c = content.substring(x.pos.start, x.pos.end)
          !(c contains "=")
        case _ => false
      }
    parsedTrees.filter(isBadValDef).foreach { badTree =>
      // Issue errors
      val positionLine = badTree.pos.line
      throw new MessageOnlyException(
        s"""[$fileName]:$positionLine: Pattern matching in val statements is not supported""".stripMargin)
    }

    val (imports: Seq[Tree], statements: Seq[Tree]) = parsedTrees partition {
      case _: Import => true
      case _         => false
    }

    /*
     * See BugInParser
     * @param t - tree
     * @param originalStatement - original
     * @return originalStatement or originalStatement with missing bracket
     */
    def parseStatementAgain(t: Tree, originalStatement: String): String = {
      val statement = scala.util.Try(parse(originalStatement, fileName, Some(reporterId))) match {
        case Failure(th) =>
          val missingText =
            findMissingText(content, t.pos.end, t.pos.line, fileName, th, Some(reporterId))
          originalStatement + missingText
        case _ =>
          originalStatement
      }
      statement
    }

    def convertStatement(t: Tree): Option[(String, Tree, LineRange)] =
      t.pos match {
        case NoPosition =>
          None
        case position =>
          val originalStatement = content.substring(position.start, position.end)
          val statement = parseStatementAgain(t, originalStatement)
          val numberLines = countLines(statement)
          Some((statement, t, LineRange(position.line - 1, position.line + numberLines)))
      }
    val stmtTreeLineRange = statements flatMap convertStatement
    val importsLineRange = importsToLineRanges(content, imports)
    (importsLineRange,
     stmtTreeLineRange.map { case (stmt, _, lr)   => (stmt, lr) },
     stmtTreeLineRange.map { case (stmt, tree, _) => (stmt, tree) })
  }

  /**
   * import sbt._, Keys._,java.util._ should return ("import sbt._, Keys._,java.util._",0)
   * @param modifiedContent - modifiedContent
   * @param imports - trees
   * @return imports per line
   */
  private def importsToLineRanges(
      modifiedContent: String,
      imports: Seq[Tree]
  ): Seq[(String, Int)] = {
    val toLineRange = imports map convertImport(modifiedContent)
    val groupedByLineNumber = toLineRange.groupBy { case (_, lineNumber) => lineNumber }
    val mergedImports = groupedByLineNumber.map {
      case (l, seq) => (l, extractLine(modifiedContent, seq))
    }
    mergedImports.toSeq.sortBy(_._1).map { case (k, v) => (v, k) }
  }

  /**
   *
   * @param modifiedContent - modifiedContent
   * @param t - tree
   * @return ((start,end),lineNumber)
   */
  private def convertImport(modifiedContent: String)(t: Tree): ((Int, Int), Int) = {
    val lineNumber = t.pos.line - 1
    ((t.pos.start, t.pos.end), lineNumber)
  }

  /**
   * Search for min begin index and max end index
   * @param modifiedContent - modifiedContent
   * @param importsInOneLine - imports in line
   * @return - text
   */
  private def extractLine(modifiedContent: String,
                          importsInOneLine: Seq[((Int, Int), Int)]): String = {
    val (begin, end) = importsInOneLine.foldLeft((Int.MaxValue, Int.MinValue)) {
      case ((min, max), ((start, end), _)) =>
        (min.min(start), max.max(end))
    }
    modifiedContent.substring(begin, end)
  }

  private def countLines(statement: String) = statement.count(c => c == END_OF_LINE_CHAR)
}

/**
 * Scala parser cuts last bracket -
 * @see https://github.com/scala/scala/pull/3991
 */
private[sbt] object MissingBracketHandler {

  /**
   *
   * @param content - parsed file
   * @param positionEnd - from index
   * @param positionLine - number of start position line
   * @param fileName - file name
   * @param originalException - original exception
   * @return missing text
   */
  private[sbt] def findMissingText(
      content: String,
      positionEnd: Int,
      positionLine: Int,
      fileName: String,
      originalException: Throwable,
      reporterId: Option[String] = Some(Random.nextInt.toString)): String = {
    findClosingBracketIndex(content, positionEnd) match {
      case Some(index) =>
        val text = content.substring(positionEnd, index + 1)
        val textWithoutBracket = text.substring(0, text.length - 1)
        scala.util.Try(SbtParser.parse(textWithoutBracket, fileName, reporterId)) match {
          case Success(_) =>
            text
          case Failure(_) =>
            findMissingText(content,
                            index + 1,
                            positionLine,
                            fileName,
                            originalException,
                            reporterId)
        }
      case _ =>
        throw new MessageOnlyException(
          s"""[$fileName]:$positionLine: ${originalException.getMessage}""".stripMargin)
    }
  }

  /**
   *
   * @param content - parsed file
   * @param from - start index
   * @return first not commented index or None
   */
  private[sbt] def findClosingBracketIndex(content: String, from: Int): Option[Int] = {
    val index = content.indexWhere(c => c == '}' || c == ')', from)
    if (index == NOT_FOUND_INDEX) {
      None
    } else {
      Some(index)
    }
  }
}
