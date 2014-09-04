package sbt

import org.specs2.mutable.Specification

class SplitExpressionsTest extends Specification with SplitExpressionsBehavior {

  "EvaluateConfigurationsOriginal" should oldExpressionsSplitter(EvaluateConfigurationsOriginal.splitExpressions)

  "EvaluateConfigurations" should oldExpressionsSplitter(EvaluateConfigurations.splitExpressions)

  "EvaluateConfigurations" should newExpressionsSplitter(EvaluateConfigurations.splitExpressions)

}