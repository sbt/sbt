package sbt.internals.parser

import java.io.File

import org.specs2.mutable.SpecificationLike

trait SplitExpression {
  def split(s: String, file: File = new File("noFile"))(implicit splitter: SplitExpressions.SplitExpression) = splitter(file, s.split("\n").toSeq)
}

trait SplitExpressionsBehavior extends SplitExpression {
  this: SpecificationLike =>

  def oldExpressionsSplitter(implicit splitter: SplitExpressions.SplitExpression): Unit = {

    "parse a simple setting" in {
      val (imports, settingsAndDefs) = split("""version := "1.0"""")
      settingsAndDefs.head._1 === """version := "1.0""""

      imports.isEmpty should beTrue
      settingsAndDefs.isEmpty should beFalse
    }

    "parse a config containing a single import" in {
      val (imports, settingsAndDefs) = split("""import foo.Bar""")
      imports.isEmpty should beFalse
      settingsAndDefs.isEmpty should beTrue
    }

    "parse a config containing two imports and a setting" in {
      val (imports, settingsAndDefs) = split(
        """import foo.Bar
              import foo.Bar

             version := "1.0"
        """.stripMargin)
      imports.size === 2
      settingsAndDefs.size === 1
    }

    "parse a config containgn a def" in {
      val (imports, settingsAndDefs) = split("""def foo(x: Int) = {
  x + 1
}""")
      imports.isEmpty should beTrue
      settingsAndDefs.isEmpty should beFalse
    }

    "parse a config containgn a val" in {
      val (imports, settingsAndDefs) = split("""val answer = 42""")
      imports.isEmpty should beTrue
      settingsAndDefs.isEmpty should beFalse
    }

    "parse a config containgn a lazy val" in {
      val (imports, settingsAndDefs) = split("""lazy val root = (project in file(".")).enablePlugins­(PlayScala)""")
      imports.isEmpty should beTrue
      settingsAndDefs.isEmpty should beFalse
    }

  }

  def newExpressionsSplitter(implicit splitter: SplitExpressions.SplitExpression): Unit = {

    "parse a two settings without intervening blank line" in {
      val (imports, settings) = split("""version := "1.0"
scalaVersion := "2.10.4"""")

      imports.isEmpty should beTrue
      settings.size === 2
    }

    "parse a setting and val without intervening blank line" in {
      val (imports, settings) = split("""version := "1.0"
lazy val root = (project in file(".")).enablePlugins­(PlayScala)""")

      imports.isEmpty should beTrue
      settings.size === 2
    }

    "parse a config containing two imports and a setting with no blank line" in {
      val (imports, settingsAndDefs) = split(
        """import foo.Bar
              import foo.Bar
             version := "1.0"
        """.stripMargin)
      imports.size === 2
      settingsAndDefs.size === 1
    }

  }

}
