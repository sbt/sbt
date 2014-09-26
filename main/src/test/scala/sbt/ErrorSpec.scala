package sbt

import java.io.File

import org.scalacheck.Gen._
import org.scalacheck.Prop._
import org.specs2.ScalaCheck

import scala.io.Source

class ErrorSpec extends AbstractSpec with ScalaCheck {
  implicit val splitter: SplitExpressions.SplitExpression = EvaluateConfigurations.splitExpressions

  "Parser " should {

    "contains file name and line number" in {
      val rootPath = getClass.getResource("").getPath + "../error-format/"
      println(s"Reading files from: $rootPath")
      foreach(new File(rootPath).listFiles) { file =>
        print(s"Processing ${file.getName}: ")
        val buildSbt = Source.fromFile(file).getLines().mkString("\n")
        SplitExpressionsNoBlankies(file, buildSbt.lines.toSeq) must throwA[MessageOnlyException].like {
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
      BugInParser.findMissingText(buildSbt, buildSbt.length, 2, "fake.txt", new MessageOnlyException("fake")) must throwA[MessageOnlyException]
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
