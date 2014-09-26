package sbt.internals.parser

import java.io.File

import sbt.LineRange

object SplitExpressions {
  type SplitExpression = (File, Seq[String]) => (Seq[(String, Int)], Seq[(String, LineRange)])
}
