package sbt
package internal
package parser

import java.io.File

import sbt.internal.util.LineRange

object SplitExpressions {
  type SplitExpression = (File, Seq[String]) => (Seq[(String, Int)], Seq[(String, LineRange)])
}
