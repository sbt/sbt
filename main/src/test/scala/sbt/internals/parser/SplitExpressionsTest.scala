package sbt.internals.parser

import org.specs2.mutable.Specification
import sbt.EvaluateConfigurations

class SplitExpressionsTest extends Specification with SplitExpressionsBehavior {

  "EvaluateConfigurationsOriginal" should oldExpressionsSplitter(
    EvaluateConfigurationsOriginal.splitExpressions
  )

  "EvaluateConfigurations" should oldExpressionsSplitter(EvaluateConfigurations.splitExpressions)

  "EvaluateConfigurations" should newExpressionsSplitter(EvaluateConfigurations.splitExpressions)

}
