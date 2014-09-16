package sbt

import java.io.File

import scala.annotation.tailrec

object EvaluateConfigurationsOriginal {

  private[this] def isSpace = (c: Char) => Character isWhitespace c
  private[this] def fstS(f: String => Boolean): ((String, Int)) => Boolean = { case (s, i) => f(s) }
  private[this] def firstNonSpaceIs(lit: String) = (_: String).view.dropWhile(isSpace).startsWith(lit)
  private[this] def or[A](a: A => Boolean, b: A => Boolean): A => Boolean = in => a(in) || b(in)

  def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    {
      val blank = (_: String).forall(isSpace)
      val isImport = firstNonSpaceIs("import ")
      val comment = firstNonSpaceIs("//")
      val blankOrComment = or(blank, comment)
      val importOrBlank = fstS(or(blankOrComment, isImport))

      val (imports, settings) = lines.zipWithIndex span importOrBlank
      (imports filterNot fstS(blankOrComment), groupedLines(settings, blank, blankOrComment))
    }

  def groupedLines(lines: Seq[(String, Int)], delimiter: String => Boolean, skipInitial: String => Boolean): Seq[(String, LineRange)] =
    {
      val fdelim = fstS(delimiter)
      @tailrec def group0(lines: Seq[(String, Int)], accum: Seq[(String, LineRange)]): Seq[(String, LineRange)] =
        if (lines.isEmpty) accum.reverse
        else {
          val start = lines dropWhile fstS(skipInitial)
          val (next, tail) = start.span { case (s, _) => !delimiter(s) }
          val grouped = if (next.isEmpty) accum else (next.map(_._1).mkString("\n"), LineRange(next.head._2, next.last._2 + 1)) +: accum
          group0(tail, grouped)
        }
      group0(lines, Nil)
    }
}