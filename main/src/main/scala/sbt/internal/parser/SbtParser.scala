package sbt
package internal
package parser

import sbt.internal.util.{ LineRange, MessageOnlyException }

import java.io.File

import sbt.internal.parser.SbtParser._

import scala.reflect.runtime.universe._

private[sbt] object SbtParser {
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
  private[parser] val NOT_FOUND_INDEX = -1
  private[sbt] val FAKE_FILE = new File("fake")
  private[parser] val XML_ERROR = "';' expected but 'val' found."
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
  def settingsTrees: Seq[(String, Tree)]

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

  private def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)]) = {
    import sbt.internal.parser.MissingBracketHandler._

    import scala.compat.Platform.EOL
    import scala.reflect.runtime._
    import scala.tools.reflect.{ ToolBox, ToolBoxError }

    val mirror = universe.runtimeMirror(this.getClass.getClassLoader)
    val toolbox = mirror.mkToolBox(options = "-Yrangepos")
    val indexedLines = lines.toIndexedSeq
    val content = indexedLines.mkString(END_OF_LINE)
    val fileName = file.getAbsolutePath

    val parsed =
      try {
        toolbox.parse(content)
      } catch {
        case e: ToolBoxError =>
          val seq = toolbox.frontEnd.infos.map { i =>
            s"""[$fileName]:${i.pos.line}: ${i.msg}"""
          }
          val errorMessage = seq.mkString(EOL)

          val error = if (errorMessage.contains(XML_ERROR)) {
            s"""
               |$errorMessage
               |Probably problem with parsing xml group, please add parens or semicolons:
               |Replace:
               |val xmlGroup = <a/><b/>
               |with:
               |val xmlGroup = (<a/><b/>)
               |or
               |val xmlGroup = <a/><b/>;
               |
             """.stripMargin
          } else {
            errorMessage
          }
          throw new MessageOnlyException(error)
      }
    val parsedTrees = parsed match {
      case Block(stmt, expr) =>
        stmt :+ expr
      case t: Tree =>
        Seq(t)
    }

    // Check No val (a,b) = foo *or* val a,b = foo as these are problematic to range positions and the WHOLE architecture.
    def isBadValDef(t: Tree): Boolean =
      t match {
        case x @ toolbox.u.ValDef(_, _, _, rhs) if rhs != toolbox.u.EmptyTree =>
          val c = content.substring(x.pos.start, x.pos.end)
          !(c contains "=")
        case _ => false
      }
    parsedTrees.filter(isBadValDef).foreach { badTree =>
      // Issue errors
      val positionLine = badTree.pos.line
      throw new MessageOnlyException(s"""[$fileName]:$positionLine: Pattern matching in val statements is not supported""".stripMargin)
    }

    val (imports, statements) = parsedTrees partition {
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
      val statement = scala.util.Try(toolbox.parse(originalStatement)) match {
        case scala.util.Failure(th) =>
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
        scala.util.Try(SbtParser(FAKE_FILE, textWithoutBracket.lines.toSeq)) match {
          case scala.util.Success(_) =>
            text
          case scala.util.Failure(th) =>
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
