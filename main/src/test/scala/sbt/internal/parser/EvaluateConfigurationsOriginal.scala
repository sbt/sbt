package sbt
package internal
package parser

import java.io.File

import sbt.internal.util.LineRange

import scala.annotation.tailrec

@deprecated("This class is be removed. Only for test backward compatibility", "1.0")
object EvaluateConfigurationsOriginal {

  def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    {
      EvaluateConfigurations.splitExpressions(lines)
    }

}
