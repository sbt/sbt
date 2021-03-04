/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

object SplitExpressionsTest extends verify.BasicTestSuite with SplitExpressionsBehavior {
  newExpressionsSplitter(EvaluateConfigurations.splitExpressions)
}
