package sbt

import java.io.File

object SplitExpressions {
  type SplitExpression = (File, Seq[String]) => (Seq[(String, Int)], Seq[(String, LineRange)])
}
