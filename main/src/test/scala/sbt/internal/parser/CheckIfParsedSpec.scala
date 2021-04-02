/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

abstract class CheckIfParsedSpec(
    implicit val splitter: SplitExpressions.SplitExpression =
      EvaluateConfigurations.splitExpressions
) extends AbstractSpec {

  test(s"${this.getClass.getName} should parse sbt file") {
    files foreach {
      case (content, description, nonEmptyImports, nonEmptyStatements) =>
        println(s"""${getClass.getSimpleName}: "$description" """)
        val (imports, statements) = split(content)
        assert(
          nonEmptyStatements == statements.nonEmpty,
          s"""$description
             |***${shouldContains(nonEmptyStatements)} statements***
             |$content """.stripMargin
        )
        assert(
          nonEmptyImports == imports.nonEmpty,
          s"""$description
             |***${shouldContains(nonEmptyImports)} imports***
             |$content """.stripMargin
        )
    }
  }

  private def shouldContains(b: Boolean): String =
    s"""Should ${if (b) {
      "contain"
    } else {
      "not contain"
    }}"""

  protected def files: Seq[(String, String, Boolean, Boolean)]

}
