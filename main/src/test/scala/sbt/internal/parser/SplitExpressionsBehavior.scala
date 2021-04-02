/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File

import sbt.internal.util.LineRange

trait SplitExpression {
  def split(s: String, file: File = new File("noFile"))(
      implicit splitter: SplitExpressions.SplitExpression
  ): (Seq[(String, Int)], Seq[(String, LineRange)]) = splitter(file, s.split("\n").toSeq)
}

trait SplitExpressionsBehavior extends SplitExpression { this: verify.BasicTestSuite =>

  def newExpressionsSplitter(implicit splitter: SplitExpressions.SplitExpression) = {

    test("parse a two settings without intervening blank line") {
      val (imports, settings) = split("""version := "1.0"
scalaVersion := "2.10.4"""")

      assert(imports.isEmpty)
      assert(settings.size == 2)
    }

    test("parse a setting and val without intervening blank line") {
      val (imports, settings) =
        split("""version := "1.0"
lazy val root = (project in file(".")).enablePlugins­(PlayScala)""")

      assert(imports.isEmpty)
      assert(settings.size == 2)
    }

    test("parse a config containing two imports and a setting with no blank line") {
      val (imports, settingsAndDefs) = split(
        """import foo.Bar
              import foo.Bar
             version := "1.0"
        """.stripMargin
      )
      assert(imports.size == 2)
      assert(settingsAndDefs.size == 1)
    }

  }

}
