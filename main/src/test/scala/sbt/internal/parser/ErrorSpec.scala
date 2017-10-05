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

import sbt.internal.util.MessageOnlyException

import scala.io.Source

class ErrorSpec extends AbstractSpec {
  implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  "Parser " should {

    "contains file name and line number" in {
      val rootPath = getClass.getClassLoader.getResource("").getPath + "/error-format/"
      println(s"Reading files from: $rootPath")
      foreach(new File(rootPath).listFiles) { file =>
        print(s"Processing ${file.getName}: ")
        val buildSbt = Source.fromFile(file).getLines().mkString("\n")
        SbtParser(file, buildSbt.lines.toSeq) must throwA[MessageOnlyException].like {
          case exp =>
            val message = exp.getMessage
            println(s"${exp.getMessage}")
            message must contain(file.getName)
        }
        containsLineNumber(buildSbt)
      }
    }

    "handle wrong parsing " in {
      val buildSbt =
        """
          |libraryDependencies ++= Seq("a" % "b" % "2") map {
          |(dependency) =>{
          | dependency
          | } /* */ //
          |}
        """.stripMargin
      MissingBracketHandler.findMissingText(
        buildSbt,
        buildSbt.length,
        2,
        "fake.txt",
        new MessageOnlyException("fake")) must throwA[MessageOnlyException]
    }

    "handle xml error " in {
      val buildSbt =
        """
          |val a = <a/><b/>
          |val s = '
        """.stripMargin
      SbtParser(SbtParser.FAKE_FILE, buildSbt.lines.toSeq) must throwA[MessageOnlyException].like {
        case exp =>
          val message = exp.getMessage
          println(s"${exp.getMessage}")
          message must contain(SbtParser.FAKE_FILE.getName)
      }
    }

  }

  private def containsLineNumber(buildSbt: String) = {
    try {
      split(buildSbt)
      throw new IllegalStateException(s"${classOf[MessageOnlyException].getName} expected")
    } catch {
      case exception: MessageOnlyException =>
        val error = exception.getMessage
        """(\d+)""".r.findFirstIn(error) match {
          case Some(x) =>
            true
          case None =>
            println(s"Number not found in $error")
            false
        }
    }
  }
}
