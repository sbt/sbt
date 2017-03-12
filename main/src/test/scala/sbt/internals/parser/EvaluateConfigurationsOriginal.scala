package sbt.internals.parser

import java.io.File

import sbt.{ EvaluateConfigurations, LineRange }

import scala.annotation.tailrec

@deprecated("This class is be removed. Only for test backward compatibility", "1.0")
object EvaluateConfigurationsOriginal {

  def splitExpressions(file: File, lines: Seq[String]): (Seq[(String, Int)], Seq[(String, LineRange)]) =
    EvaluateConfigurations.splitExpressions(lines)

}
