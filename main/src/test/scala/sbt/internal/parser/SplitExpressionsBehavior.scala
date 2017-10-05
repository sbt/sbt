/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package internal
package parser

import java.io.File

import org.specs2.mutable.SpecificationLike

trait SplitExpression {
  def split(s: String, file: File = new File("noFile"))(
      implicit splitter: SplitExpressions.SplitExpression) = splitter(file, s.split("\n").toSeq)
}

trait SplitExpressionsBehavior extends SplitExpression { this: SpecificationLike =>

  def newExpressionsSplitter(implicit splitter: SplitExpressions.SplitExpression): Unit = {

    "parse a two settings without intervening blank line" in {
      val (imports, settings) = split("""version := "1.0"
scalaVersion := "2.10.4"""")

      imports.isEmpty should beTrue
      settings.size === 2
    }

    "parse a setting and val without intervening blank line" in {
      val (imports, settings) =
        split("""version := "1.0"
lazy val root = (project in file(".")).enablePlugins­(PlayScala)""")

      imports.isEmpty should beTrue
      settings.size === 2
    }

    "parse a config containing two imports and a setting with no blank line" in {
      val (imports, settingsAndDefs) = split(
        """import foo.Bar
              import foo.Bar
             version := "1.0"
        """.stripMargin
      )
      imports.size === 2
      settingsAndDefs.size === 1
    }

  }

}
