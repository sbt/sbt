package sbt.compiler.javac

import java.io.File

import org.specs2.matcher.MatchResult
import sbt.Logger
import org.specs2.Specification

object JavaErrorParserSpec extends Specification {
  def is = s2"""

  This is a specification for parsing of java error messages.

  The JavaErrorParser should
     be able to parse linux errors    $parseSampleLinux
     be able to parse windows file names $parseWindowsFile
     be able to parse windows errors  $parseSampleWindows
     be able to parse javac errors $parseSampleJavac
  """

  def parseSampleLinux: MatchResult[_] = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleLinuxMessage, logger)
    def rightSize = problems must haveSize(1)
    def rightFile =
      problems(0).position.sourcePath.get must beEqualTo("/home/me/projects/sample/src/main/Test.java")
    rightSize and rightFile
  }

  def parseSampleWindows: MatchResult[_] = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleWindowsMessage, logger)
    def rightSize = problems must haveSize(1)
    def rightFile = problems(0).position.sourcePath.get must beEqualTo(windowsFile)
    rightSize and rightFile
  }

  def parseWindowsFile: MatchResult[_] = {
    val parser = new JavaErrorParser()
    def failure = false must beTrue
    parser.parse(parser.fileAndLineNo, sampleWindowsMessage) match {
      case parser.Success((file, line), rest) => file must beEqualTo(windowsFile)
      case parser.Error(msg, next)            => failure.setMessage(s"Error to parse: $msg, ${next.pos.longString}")
      case parser.Failure(msg, next)          => failure.setMessage(s"Failed to parse: $msg, ${next.pos.longString}")
    }
  }

  def parseSampleJavac: MatchResult[_] = {
    val parser = new JavaErrorParser()
    val logger = Logger.Null
    val problems = parser.parseProblems(sampleJavacMessage, logger)
    def rightSize = problems must haveSize(1)
    def rightError = problems(0).message must beEqualTo(sampleJavacMessage)
    rightSize and rightError
  }

  def sampleLinuxMessage: String =
    """
      |/home/me/projects/sample/src/main/Test.java:4: cannot find symbol
      |symbol  : method baz()
      |location: class Foo
      |return baz();
    """.stripMargin

  def sampleWindowsMessage: String =
    s"""
       |$windowsFile:4: cannot find symbol
       |symbol  : method baz()
       |location: class Foo
       |return baz();
    """.stripMargin

  def windowsFile: String = """C:\Projects\sample\src\main\java\Test.java"""
  def windowsFileAndLine: String = s"""$windowsFile:4"""

  def sampleJavacMessage = "javac: invalid flag: -foobar"
}
