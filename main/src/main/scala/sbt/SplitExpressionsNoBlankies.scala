package sbt

import java.io.File

import scala.annotation.tailrec
import SplitExpressionsNoBlankies._
import scala.reflect.runtime.universe._

object SplitExpressionsNoBlankies {
  val END_OF_LINE_CHAR = '\n'
  val END_OF_LINE = String.valueOf(END_OF_LINE_CHAR)
}

case class SplitExpressionsNoBlankies(file: File, lines: Seq[String]) {
  //settingsTrees needed for "session save"
  val (imports, settings, settingsTrees) = splitExpressions(file, lines)

  private def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)], Seq[(String, Tree)]) = {
    import scala.reflect.runtime._

    import scala.tools.reflect.ToolBoxError
    import scala.tools.reflect.ToolBox
    import scala.compat.Platform.EOL
    import BugInParser._
    import XmlContent._

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
          val missingText = tryWithNextStatement(modifiedContent, t.pos.end, t.pos.line, fileName, th)
          originalStatement + missingText
        case _ =>
          originalStatement
      }
      statement
    }

    def convertStatement(t: Tree): Option[(String, Tree, LineRange)] =
      if (t.pos.isDefined) {
        val originalStatement = modifiedContent.substring(t.pos.start, t.pos.end)
        val statement = parseStatementAgain(t, originalStatement)
        val numberLines = statement.count(c => c == END_OF_LINE_CHAR)
        Some((statement, t, LineRange(t.pos.line - 1, t.pos.line + numberLines)))
      } else {
        None
      }
    val statementsTreeLineRange = statements flatMap convertStatement
    (imports map convertImport, statementsTreeLineRange.map(t => (t._1, t._3)), statementsTreeLineRange.map(t => (t._1, t._2)))
  }
}

/**
 * Scala parser cuts last bracket -
 * @see http://stackoverflow.com/questions/25547149/scala-parser-cuts-last-bracket
 */
private[sbt] object BugInParser {
  /**
   *
   * @param content - parsed file
   * @param positionEnd - from index
   * @param positionLine - number of start position line
   * @param fileName - file name
   * @param th - original exception
   * @return
   */
  private[sbt] def tryWithNextStatement(content: String, positionEnd: Int, positionLine: Int, fileName: String, th: Throwable): String = {
    findFirstNotBlankNotCommentedIndex(content, positionEnd) match {
      case Some(index) =>
        content.substring(positionEnd, index + 1)
      case _ =>
        throw new MessageOnlyException(s"""[$fileName]:$positionLine: ${th.getMessage}""".stripMargin)
    }
  }

  /**
   *
   * @param content - parsed file
   * @param from - start index
   * @return first not commented index or None
   */
  private def findFirstNotBlankNotCommentedIndex(content: String, from: Int): Option[Int] = {
    val index = content.indexWhere(c => !c.isWhitespace, from)
    if (index == -1) {
      None
    } else {
      val c = content.charAt(index)
      if (c == '/' && content.size > index + 1) {
        val nextChar = content.charAt(index + 1)
        if (nextChar == '/') {
          val endOfLine = content.indexOf('\n', index)
          findFirstNotBlankNotCommentedIndex(content, endOfLine)
        } else {
          //if (nextChar == '*')
          val endOfCommented = content.indexOf("*/", index + 1)
          findFirstNotBlankNotCommentedIndex(content, endOfCommented + 2)
        }
      } else {
        Some(index)
      }
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
private object XmlContent {
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
   * @return (text,true) - is xml (text,false) - if normal text
   */
  private def splitFile(content: String, ts: Seq[(String, Int, Int)]): Seq[(String, Boolean)] = {
    val (statements, index) = ts.foldLeft((Seq.empty[(String, Boolean)], 0)) {
      (accSeqIndex, el) =>
        val (statement, startIndex, endIndex) = el
        val (accSeq, index) = accSeqIndex
        val textStatementOption = if (index >= startIndex) {
          None
        } else {
          val s = content.substring(index, startIndex)
          Some((s, false))
        }
        val newAccSeq = (statement, true) +: addOptionToCollection(accSeq, textStatementOption)
        (newAccSeq, endIndex)
    }
    val endOfFile = content.substring(index, content.length)
    val withEndOfFile = (endOfFile, false) +: statements
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
    if (spaceIndex == -1) {
      subs
    } else {
      subs.substring(0, spaceIndex)
    }
  }

  /**
   * Modified Opening Tag - <aaa/>
   * @param offsetIndex - index
   * @param acc - result
   * @return Set with tags and positions
   */
  @tailrec
  private def findModifiedOpeningTags(content: String, offsetIndex: Int, acc: Seq[(String, Int, Int)]): Seq[(String, Int, Int)] = {
    val endIndex = content.indexOf("/>", offsetIndex)
    if (endIndex == -1) {
      acc
    } else {
      val xmlFragment = findModifiedOpeningTag(content, offsetIndex, endIndex)
      val newAcc = addOptionToCollection(acc, xmlFragment)
      findModifiedOpeningTags(content, endIndex + 2, newAcc)
    }
  }

  private def findModifiedOpeningTag(content: String, offsetIndex: Int, endIndex: Int): Option[(String, Int, Int)] = {
    val startIndex = content.substring(offsetIndex, endIndex).lastIndexOf("<")
    if (startIndex == -1) {
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
    if (index == -1) {
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
    if (closeTagStartIndex == -1) {
      acc
    } else {
      val closeTagEndIndex = content.indexOf(">", closeTagStartIndex)
      if (closeTagEndIndex == -1) {
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
    val statements: Seq[(String, Boolean)] = splitFile(content, xmlParts)
    val (builder, wasPreviousXml, wasXml, _) = statements.foldLeft((Seq.empty[String], false, false, "")) {
      (acc, el) =>
        val (bAcc, wasXml, _, previous) = acc
        val (content, isXml) = el
        val contentEmpty = content.trim.isEmpty
        val (isNotCommentedXml, newAcc) = if (isXml) {
          if (!wasXml) {
            if (areBracketsNecessary(previous)) {
              (true, " ( " +: bAcc)
            } else {
              (false, bAcc)
            }
          } else {
            (true, bAcc)
          }
        } else if (wasXml && !contentEmpty) {
          (false, " ) " +: bAcc)
        } else {
          (false, bAcc)
        }

        (content +: newAcc, isNotCommentedXml || (wasXml && contentEmpty), isXml, content)
    }
    val closeIfNecessaryBuilder =
      if (wasPreviousXml && !wasXml) {
        builder.head +: " ) " +: builder.tail
      } else {
        builder
      }
    closeIfNecessaryBuilder.reverse.mkString
  }

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
      if (openTagIndex == -1) {
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
    val doubleSlash = statement.indexOf("//")
    val endOfLine = statement.indexOf(END_OF_LINE)
    if (doubleSlash == -1 || (doubleSlash < endOfLine)) {
      val roundBrackets = statement.lastIndexOf("(")
      val braces = statement.lastIndexOf("{")
      val max = roundBrackets.max(braces)
      if (max == -1) {
        true
      } else {
        val trimmed = statement.substring(max + 1).trim
        trimmed.nonEmpty
      }
    } else {
      false
    }
  }
}