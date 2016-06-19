package sbt
package internal
package parser

import org.specs2.mutable.Specification

class SplitExpressionsTest extends Specification with SplitExpressionsBehavior {

  "EvaluateConfigurations" should newExpressionsSplitter(EvaluateConfigurations.splitExpressions)

}
