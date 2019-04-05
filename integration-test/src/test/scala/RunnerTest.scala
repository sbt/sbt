package example.test

import minitest._
import scala.sys.process._
import java.io.File

object SbtRunnerTest extends SimpleTestSuite with PowerAssertions {
  lazy val sbtScript = new File("target/universal/stage/bin/sbt")
  def sbtProcess(arg: String) =
    sbt.internal.Process(sbtScript.getAbsolutePath + " " + arg, new File("citest"),
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> "")
  def sbtProcessWithOpts(arg: String) =
    sbt.internal.Process(sbtScript.getAbsolutePath + " " + arg, new File("citest"),
      "JAVA_OPTS" -> "-Xmx1024m",
      "SBT_OPTS" -> "")

  test("sbt runs") {
    assert(sbtScript.exists)
    val out = sbtProcess("compile -v").!
    assert(out == 0)
    ()
  }

  test("sbt -no-colors") {
    val out = sbtProcess("compile -no-colors -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --no-colors") {
    val out = sbtProcess("compile --no-colors -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --color=false") {
    val out = sbtProcess("compile --color=false -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.color=false"))
    ()
  }

  test("sbt --supershell=never") {
    val out = sbtProcess("compile --supershell=never -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.supershell=never"))
    ()
  }

  test("sbt --timings") {
    val out = sbtProcess("compile --timings -v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.task.timings=true"))
    ()
  }

  test("sbt -mem 503") {
    val out = sbtProcess("compile -mem 503 -v").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    ()
  }

  test("sbt -mem 503 with JAVA_OPTS") {
    val out = sbtProcessWithOpts("compile -mem 503 -v").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    ()
  }
}
