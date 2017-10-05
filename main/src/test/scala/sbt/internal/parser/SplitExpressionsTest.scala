/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import org.specs2.mutable.Specification

class SplitExpressionsTest extends Specification with SplitExpressionsBehavior {

  "EvaluateConfigurations" should newExpressionsSplitter(EvaluateConfigurations.splitExpressions)

}
