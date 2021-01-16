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
import sbt.internal.util.MessageOnlyException
import scala.io.Source

object ErrorSpec extends AbstractSpec {
  implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  test("Parser should contains file name and line number") {
    val rootPath = getClass.getResource("/error-format/").getPath
    println(s"Reading files from: $rootPath")
    new File(rootPath).listFiles foreach { file =>
      print(s"Processing ${file.getName}: ")
      val buildSbt = Source.fromFile(file).getLines().mkString("\n")
      try {
        SbtParser(file, buildSbt.linesIterator.toSeq)
      } catch {
        case exp: MessageOnlyException =>
          val message = exp.getMessage
          println(s"${exp.getMessage}")
          assert(message.contains(file.getName))
      }
      containsLineNumber(buildSbt)
    }
  }

  test("it should handle wrong parsing") {
    intercept[MessageOnlyException] {
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
        new MessageOnlyException("fake")
      )
      ()
    }
  }

  test("it should handle xml error") {
    try {
      val buildSbt =
        """
          |val a = <a/><b/>
          |val s = '
        """.stripMargin
      SbtParser(SbtParser.FAKE_FILE, buildSbt.linesIterator.toSeq)
    } catch {
      case exp: MessageOnlyException =>
        val message = exp.getMessage
        println(s"${exp.getMessage}")
        assert(message.contains(SbtParser.FAKE_FILE.getName))
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
          case Some(_) => true
          case None =>
            println(s"Number not found in $error")
            false
        }
    }
  }
}
