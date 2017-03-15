package sbt
package internal
package parser

import sbt.internal.util.{ LineRange, MessageOnlyException }

import java.io.File

import sbt.internal.parser.SbtParser._

import scala.compat.Platform.EOL
import scala.reflect.internal.util.BatchSourceFile
import scala.reflect.io.VirtualDirectory
import scala.reflect.internal.Positions
import scala.tools.nsc.{ CompilerCommand, Global }
import scala.tools.nsc.reporters.StoreReporter

import scala.util.{Success, Failure}

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
   * This is a sign that this whole parser should be rewritten.
   * There are exceptions everywhere and the logic to work around
   * the scalac parser bug heavily relies on them and it's tied
   * to the test suite. Ideally, we only want to throw exceptions
   * when we know for a fact that the user-provided snippet doesn't
   * parse.
   */
  private[sbt] class ParserStoreReporter extends StoreReporter {
    def throwParserErrorsIfAny(fileName: String): Unit = {
      if (parserReporter.hasErrors) {
        val seq = parserReporter.infos.map { info =>
          s"""[$fileName]:${info.pos.line}: ${info.msg}"""
        }
        val errorMessage = seq.mkString(EOL)
        val error: String =
          if (errorMessage.contains(XML_ERROR))
            s"$errorMessage\n${SbtParser.XmlErrorMessage}"
          else errorMessage
        throw new MessageOnlyException(error)
      }
    }
  }

  private[sbt] final val parserReporter = new ParserStoreReporter

  private[sbt] final lazy val defaultGlobalForParser = {
    import scala.reflect.internal.util.NoPosition
    val options = "-cp" :: s"$defaultClasspath" :: "-Yrangepos" :: Nil
    val reportError = (msg: String) => parserReporter.error(NoPosition, msg)
    val command = new CompilerCommand(options, reportError)
    val settings = command.settings
    settings.outputDirs.setSingleOutput(new VirtualDirectory("(memory)", None))

    // Mixing Positions is necessary, otherwise global ignores -Yrangepos
    val global = new Global(settings, parserReporter) with Positions
    val run = new global.Run
    // Necessary to have a dummy unit for initialization...
    val initFile = new BatchSourceFile("<wrapper-init>", "")
    val _ = new global.CompilationUnit(initFile)
    global.phase = run.parserPhase
    global
  }

  import defaultGlobalForParser.Tree

  /**
   * Parse code reusing the same [[Run]] instance.
   *
   * The access to this method has to be synchronized (no more than one
   * thread can access to it at the same time since it reuses the same
   * [[Global]], which mutates the whole universe).
   */
  private[sbt] def parse(code: String, fileName: String): Seq[Tree] = synchronized {
    import defaultGlobalForParser._
    parserReporter.reset()
    val wrapperFile = new BatchSourceFile("<wrapper>", code)
    val unit = new CompilationUnit(wrapperFile)
    val parser = new syntaxAnalyzer.UnitParser(unit)
    val parsedTrees = parser.templateStats()
    parser.accept(scala.tools.nsc.ast.parser.Tokens.EOF)
    parserReporter.throwParserErrorsIfAny(fileName)
    parsedTrees
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

  private def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)]) = {
    import sbt.internal.parser.MissingBracketHandler.findMissingText

    val indexedLines = lines.toIndexedSeq
    val content = indexedLines.mkString(END_OF_LINE)
    val fileName = file.getAbsolutePath
    val parsedTrees: Seq[Tree] = parse(content, fileName)

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
      throw new MessageOnlyException(s"""[$fileName]:$positionLine: Pattern matching in val statements is not supported""".stripMargin)
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
      val statement = scala.util.Try(parse(originalStatement, fileName)) match {
        case Failure(th) =>
          val missingText = findMissingText(content, t.pos.end, t.pos.line, fileName, th)
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
    (importsLineRange, stmtTreeLineRange.map { case (stmt, _, lr) => (stmt, lr) }, stmtTreeLineRange.map { case (stmt, tree, _) => (stmt, tree) })
  }

  /**
   * import sbt._, Keys._,java.util._ should return ("import sbt._, Keys._,java.util._",0)
   * @param modifiedContent - modifiedContent
   * @param imports - trees
   * @return imports per line
   */
  private def importsToLineRanges(modifiedContent: String, imports: Seq[Tree]): Seq[(String, Int)] = {
    val toLineRange = imports map convertImport(modifiedContent)
    val groupedByLineNumber = toLineRange.groupBy { case (_, lineNumber) => lineNumber }
    val mergedImports = groupedByLineNumber.map { case (l, seq) => (l, extractLine(modifiedContent, seq)) }
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
  private def extractLine(modifiedContent: String, importsInOneLine: Seq[((Int, Int), Int)]): String = {
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
  private[sbt] def findMissingText(content: String, positionEnd: Int, positionLine: Int, fileName: String, originalException: Throwable): String = {
    findClosingBracketIndex(content, positionEnd) match {
      case Some(index) =>
        val text = content.substring(positionEnd, index + 1)
        val textWithoutBracket = text.substring(0, text.length - 1)
        scala.util.Try(SbtParser.parse(textWithoutBracket, fileName)) match {
          case Success(_) =>
            text
          case Failure(th) =>
            findMissingText(content, index + 1, positionLine, fileName, originalException)
        }
      case _ =>
        throw new MessageOnlyException(s"""[$fileName]:$positionLine: ${originalException.getMessage}""".stripMargin)
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
