package sbt
package internals
package parser

import java.io.File

import sbt.internals.parser.SbtParser._

import scala.annotation.tailrec
import scala.reflect.runtime.universe._

private[sbt] object SbtParser {
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
  private[parser] val NOT_FOUND_INDEX = -1
  private[sbt] val FAKE_FILE = new File("fake")
}

/**
 * This method soley exists to add scaladoc to members in SbtParser which
 * are defined using pattern matching.
 */
sealed trait ParsedSbtFileExpressions {
  /** The set of parsed import expressions. */
  def imports: Seq[(String, Int)]

  /** The set of parsed defintions and/or sbt build settings. */
  def settings: Seq[(String, LineRange)]

  /** The set of scala tree's for parsed definitions/settings and the underlying string representation.. */
  def settingsTrees: Seq[(String, Tree)]

  /** Represents the changes we had to perform to the sbt file so that XML will parse correctly. */
  def modifiedContent: String
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
  // TODO - We should look into splitting out "defintiions" vs. "settings" here instead of further string lookups, since we have the
  // parsed trees.
  val (imports, settings, settingsTrees, modifiedContent) = splitExpressions(file, lines)

  private def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)], String) = {
    import sbt.internals.parser.MissingBracketHandler._
    import sbt.internals.parser.XmlContent._

    import scala.compat.Platform.EOL
    import scala.reflect.runtime._
    import scala.tools.reflect.{ ToolBox, ToolBoxError }

    val mirror = universe.runtimeMirror(this.getClass.getClassLoader)
    val toolbox = mirror.mkToolBox(options = "-Yrangepos")
    val indexedLines = lines.toIndexedSeq
    val original = indexedLines.mkString(END_OF_LINE)
    val modifiedContent = handleXmlContent(original)
    val fileName = file.getAbsolutePath

    val parsed =
      try {
        toolbox.parse(modifiedContent)
      } catch {
        case e: ToolBoxError =>
          val seq = toolbox.frontEnd.infos.map { i =>
            s"""[$fileName]:${i.pos.line}: ${i.msg}"""
          }
          throw new MessageOnlyException(
            s"""======
               |$modifiedContent
               |======
               |${seq.mkString(EOL)}""".stripMargin)
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
          val content = modifiedContent.substring(x.pos.start, x.pos.end)
          !(content contains "=")
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

    def convertImport(t: Tree): (String, Int) =
      (modifiedContent.substring(t.pos.start, t.pos.end), t.pos.line - 1)

    /**
     * See BugInParser
     * @param t - tree
     * @param originalStatement - original
     * @return originalStatement or originalStatement with missing bracket
     */
    def parseStatementAgain(t: Tree, originalStatement: String): String = {
      val statement = util.Try(toolbox.parse(originalStatement)) match {
        case util.Failure(th) =>
          val missingText = findMissingText(modifiedContent, t.pos.end, t.pos.line, fileName, th)
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
          val originalStatement = modifiedContent.substring(position.start, position.end)
          val statement = parseStatementAgain(t, originalStatement)
          val numberLines = countLines(statement)
          Some((statement, t, LineRange(position.line - 1, position.line + numberLines)))
      }
    val stmtTreeLineRange = statements flatMap convertStatement
    (imports map convertImport, stmtTreeLineRange.map { case (stmt, _, lr) => (stmt, lr) }, stmtTreeLineRange.map { case (stmt, tree, _) => (stmt, tree) }, modifiedContent)
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
        util.Try(SbtParser(FAKE_FILE, textWithoutBracket.lines.toSeq)) match {
          case util.Success(_) =>
            text
          case util.Failure(th) =>
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

/**
 * #ToolBox#parse(String) will fail for xml sequence:
 * <pre>
 * val xml = <div>txt</div>
 * <a>rr</a>
 * </pre>
 * At least brackets have to be added
 * <pre>
 * val xml = (<div>txt</div>
 * <a>rr</a>)
 * </pre>
 */
private[sbt] object XmlContent {

  private val OPEN_PARENTHESIS = '{'

  private val OPEN_CURLY_BRACKET = '('

  private val DOUBLE_SLASH = "//"

  private val OPEN_BRACKET = s"$OPEN_CURLY_BRACKET"

  private val CLOSE_BRACKET = ")"

  /**
   *
   * @param original - file content
   * @return original content or content with brackets added to xml parts
   */
  private[sbt] def handleXmlContent(original: String): String = {
    val xmlParts = findXmlParts(original)
    if (xmlParts.isEmpty) {
      original
    } else {
      addExplicitXmlContent(original, xmlParts)
    }
  }

  /**
   * Cut file for normal text - xml - normal text - xml ....
   * @param content - content
   * @param ts - import/statements
   * @return Seq - Right(xml,whiteSpaces) - for xml, Left(statement) - for text
   */
  private def splitFile(content: String, ts: Seq[(String, Int, Int)]): Seq[Either[(String), (String, String)]] = {
    val (statements, lastIndex) = ts.foldLeft((Seq.empty[Either[(String), (String, String)]], 0)) {
      case ((accSeq, index), (statement, startIndex, endIndex)) =>
        val toAdd = if (index >= startIndex) {
          Seq(Right((statement, "")))
        } else {
          val s = content.substring(index, startIndex)
          if (s.trim.isEmpty) {
            Seq(Right((statement, s)))
          } else {
            Seq(Right((statement, "")), Left(s))
          }
        }
        (toAdd ++ accSeq, endIndex)
    }
    val endOfFile = content.substring(lastIndex, content.length)
    val withEndOfFile = if (endOfFile.isEmpty) statements else Left(endOfFile) +: statements
    withEndOfFile.reverse
  }

  /**
   * Cut potential xmls from content
   * @param content - content
   * @return sorted by openIndex xml parts
   */
  private def findXmlParts(content: String): Seq[(String, Int, Int)] = {
    val xmlParts = findModifiedOpeningTags(content, 0, Seq.empty) ++ findNotModifiedOpeningTags(content, 0, Seq.empty)
    val rootXmlParts = removeEmbeddedXmlParts(xmlParts)
    rootXmlParts.sortBy(z => z._2)

  }

  private def searchForTagName(text: String, startIndex: Int, endIndex: Int) = {
    val subs = text.substring(startIndex, endIndex)
    val spaceIndex = subs.indexWhere(c => c.isWhitespace, 1)
    if (spaceIndex == NOT_FOUND_INDEX) {
      subs
    } else {
      subs.substring(0, spaceIndex)
    }
  }

  private val CLOSE_XML_TAG = "/>"

  /**
   * Modified Opening Tag - <aaa/>
   * @param offsetIndex - index
   * @param acc - result
   * @return Set with tags and positions
   */
  @tailrec
  private def findModifiedOpeningTags(content: String, offsetIndex: Int, acc: Seq[(String, Int, Int)]): Seq[(String, Int, Int)] = {
    val endIndex = content.indexOf(CLOSE_XML_TAG, offsetIndex)
    if (endIndex == NOT_FOUND_INDEX) {
      acc
    } else {
      val xmlFragment = findModifiedOpeningTag(content, offsetIndex, endIndex)
      val newAcc = addOptionToCollection(acc, xmlFragment)
      findModifiedOpeningTags(content, endIndex + 2, newAcc)
    }
  }

  private def findModifiedOpeningTag(content: String, offsetIndex: Int, endIndex: Int): Option[(String, Int, Int)] = {
    val startIndex = content.substring(offsetIndex, endIndex).lastIndexOf("<")
    if (startIndex == NOT_FOUND_INDEX) {
      None
    } else {
      val tagName = searchForTagName(content, startIndex + 1 + offsetIndex, endIndex)
      if (xml.Utility.isName(tagName)) {
        xmlFragmentOption(content, startIndex + offsetIndex, endIndex + 2)
      } else {
        None
      }
    }

  }

  private def searchForOpeningIndex(text: String, closeTagStartIndex: Int, tagName: String) = {
    val subs = text.substring(0, closeTagStartIndex)
    val index = subs.lastIndexOf(s"<$tagName>")
    if (index == NOT_FOUND_INDEX) {
      subs.lastIndexOf(s"<$tagName ")
    } else {
      index
    }
  }

  /**
   * Xml like - <aaa>...<aaa/>
   * @param current - index
   * @param acc - result
   * @return Set with tags and positions
   */
  @tailrec
  private def findNotModifiedOpeningTags(content: String, current: Int, acc: Seq[(String, Int, Int)]): Seq[(String, Int, Int)] = {
    val closeTagStartIndex = content.indexOf("</", current)
    if (closeTagStartIndex == NOT_FOUND_INDEX) {
      acc
    } else {
      val closeTagEndIndex = content.indexOf(">", closeTagStartIndex)
      if (closeTagEndIndex == NOT_FOUND_INDEX) {
        findNotModifiedOpeningTags(content, closeTagStartIndex + 2, acc)
      } else {
        val xmlFragment = findNotModifiedOpeningTag(content, closeTagStartIndex, closeTagEndIndex)
        val newAcc = addOptionToCollection(acc, xmlFragment)
        findNotModifiedOpeningTags(content, closeTagEndIndex + 1, newAcc)
      }
    }
  }

  private def removeEmbeddedXmlParts(xmlParts: Seq[(String, Int, Int)]) = {
    def isElementBetween(el: (String, Int, Int), open: Int, close: Int): Boolean = {
      xmlParts.exists {
        element =>
          val (_, openIndex, closeIndex) = element
          el != element && (open > openIndex) && (close < closeIndex)
      }
    }
    xmlParts.filterNot { el =>
      val (_, open, close) = el
      isElementBetween(el, open, close)
    }
  }

  /**
   *
   * @param content - content
   * @param xmlParts - xmlParts
   * @return content with xml with brackets
   */
  private def addExplicitXmlContent(content: String, xmlParts: Seq[(String, Int, Int)]): String = {
    val statements = splitFile(content, xmlParts)

    val (_, seq, lastAdd) = statements.foldLeft[(Option[Either[(String), (String, String)]], Seq[String], Boolean)]((None, Seq.empty[String], false)) {
      case ((previousOption, acc, add), element) =>
        val (newAcc, newAdd) = (element, previousOption) match {
          case (Left(text), _) =>
            val accWithClose = if (add) {
              addCloseBracket(acc)
            } else {
              acc
            }
            (text +: accWithClose, false)
          case (Right((xml, nonXml)), Some(Left(text))) =>
            val (accWithOpen, added) = if (areBracketsNecessary(text)) {
              (OPEN_BRACKET +: acc, true)
            } else {
              (acc, false)
            }
            (xml +: (nonXml +: accWithOpen), added)
          case (Right((xml, nonXml)), _) =>
            (xml +: (nonXml +: acc), add)
        }
        (Some(element), newAcc, newAdd)
    }

    val correctedSeq = if (lastAdd) {
      addCloseBracket(seq)
    } else {
      seq
    }
    correctedSeq.reverse.mkString
  }

  private def addCloseBracket(statements: Seq[String]) = CLOSE_BRACKET +: statements

  /**
   * Add to head if option is not empty
   * @param ts - seq
   * @param option - option
   * @tparam T - type
   * @return original seq for None, add to head for Some[T]
   */
  private def addOptionToCollection[T](ts: Seq[T], option: Option[T]) = option.fold(ts)(el => el +: ts)

  private def findNotModifiedOpeningTag(content: String, closeTagStartIndex: Int, closeTagEndIndex: Int): Option[(String, Int, Int)] = {

    val tagName = content.substring(closeTagStartIndex + 2, closeTagEndIndex)
    if (xml.Utility.isName(tagName)) {
      val openTagIndex = searchForOpeningIndex(content, closeTagStartIndex, tagName)
      if (openTagIndex == NOT_FOUND_INDEX) {
        None
      } else {
        xmlFragmentOption(content, openTagIndex, closeTagEndIndex + 1)
      }
    } else {
      None
    }
  }

  /**
   * Check, if xmlPart is valid xml. If not - None is returned
   * @param content - file content
   * @param openIndex - open index
   * @param closeIndex - close index
   * @return Some((String,Int,Int))
   */
  private def xmlFragmentOption(content: String, openIndex: Int, closeIndex: Int): Option[(String, Int, Int)] = {
    val xmlPart = content.substring(openIndex, closeIndex)
    util.Try(xml.XML.loadString(xmlPart)) match {
      case util.Success(_)  => Some((xmlPart, openIndex, closeIndex))
      case util.Failure(th) => None
    }
  }

  /**
   * If xml is in brackets - we do not need to add them
   * @param statement - statement
   * @return are brackets necessary?
   */
  private def areBracketsNecessary(statement: String): Boolean = {
    val doubleSlash = statement.indexOf(DOUBLE_SLASH)

    if (doubleSlash != NOT_FOUND_INDEX) {
      val endOfLine = statement.indexOf(END_OF_LINE, doubleSlash)
      if (endOfLine == NOT_FOUND_INDEX) {
        false
      } else {
        areBracketsNecessary(statement.substring(endOfLine))
      }
    } else {
      val roundBrackets = statement.lastIndexOf(OPEN_CURLY_BRACKET)
      val braces = statement.lastIndexOf(OPEN_PARENTHESIS)
      val max = roundBrackets.max(braces)
      if (max == NOT_FOUND_INDEX) {
        true
      } else {
        val trimmed = statement.substring(max + 1).trim
        trimmed.nonEmpty
      }

    }
  }
}