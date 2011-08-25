package org.improving

import scala.tools.nsc.io._
import org.specs._

object SbtRunnerTest extends Specification {
  val scripts = {
    import Predef._
    List[String](
      """|sbt -sbt-create -sbt-snapshot -210
         |sbt update
         |sbt about
      """,
      """|sbt -sbt-create -sbt-snapshot -29
         |sbt update
         |sbt version
      """,
      """|sbt -sbt-create -sbt-version 0.7.7 -28
         |sbt help
         |sbt -h
      """
    ) map (_.trim.stripMargin.lines.toList)
  }
  
  val singles = """
sbt -v -d -no-colors update package
sbt -verbose -210 -debug -ivy /tmp update
""".trim.lines

  import scala.sys.process._

  def sbtProjectLines(lines: List[String]) = {
    println("Running: " + lines.mkString(", "))
    
    val dir    = Directory.makeTemp("sbt-runner-test").jfile
    val result = lines map (x => Process(x, dir)) reduceLeft (_ #&& _) ! ;

    result == 0
  }
  def sbtProjectLine(line: String) =
    sbtProjectLines(List("sbt -sbt-create version", line))
  
  "Sbt Runner" should {
    "deal with lots of different command lines" in {
      singles foreach (x => sbtProjectLine(x) mustEqual true)
    }
    "handle various command sequences" in {
      scripts foreach (xs => sbtProjectLines(xs) mustEqual true)
    }
  }
}
