/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import sbt.internal.util.LineRange
import xsbti.VirtualFileRef

trait SplitExpression {
  extension (splitter: SplitExpressions.SplitExpression)
    def apply(s: String): (Seq[(String, Int)], Seq[(String, LineRange)]) =
      splitter(VirtualFileRef.of("noFile"), s.split('\n').toSeq)
}

trait SplitExpressionsBehavior extends SplitExpression { this: verify.BasicTestSuite =>

  def newExpressionsSplitter(splitter: SplitExpressions.SplitExpression) = {

    test("parse a two settings without intervening blank line") {
      val (imports, settings) = splitter(
        """|version := "1.0"
           |scalaVersion := "2.10.4"""".stripMargin
      )

      assert(imports.isEmpty)
      assert(settings.size == 2)
    }

    test("parse a setting and val without intervening blank line") {
      val (imports, settings) = splitter(
        """|version := "1.0"
           |lazy val root = (project in file(".")).enablePlugins(PlayScala)""".stripMargin
      )

      assert(imports.isEmpty)
      assert(settings.size == 2)
    }

    test("parse a config containing two imports and a setting with no blank line") {
      val (imports, settingsAndDefs) = splitter(
        """|import foo.Bar
           |import foo.Bar
           |version := "1.0"
           |""".stripMargin
      )
      assert(imports.size == 2)
      assert(settingsAndDefs.size == 1)
    }
  }

}
