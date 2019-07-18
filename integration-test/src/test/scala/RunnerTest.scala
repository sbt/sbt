package example.test

import minitest._
import scala.sys.process._
import java.io.File

object SbtRunnerTest extends SimpleTestSuite with PowerAssertions {
  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")
  lazy val sbtScript =
    if (isWindows) new File("target/universal/stage/bin/sbt.bat")
    else new File("target/universal/stage/bin/sbt")
  def sbtProcess(arg: String) =
    sbt.internal.Process(sbtScript.getAbsolutePath + " " + arg, new File("citest"),
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> "")
  def sbtProcessWithOpts(arg: String, javaOpts: String, sbtOpts: String) =
    sbt.internal.Process(sbtScript.getAbsolutePath + " " + arg, new File("citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts)

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

  test("sbt with -mem 503 in JAVA_OPTS") {
    val out = sbtProcessWithOpts("compile -mem 503 -v", "-Xmx1024m", "").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    ()
  }

  test("sbt with -Xms2048M -Xmx2048M -Xss6M in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "-Xms2048M -Xmx2048M -Xss6M").!!.linesIterator.toList
    assert(out.contains[String]("-Xss6M"))
    ()
  }

  test("sbt with --no-colors in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile -v", "", "--no-colors").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt -V|-version should print sbtVersion") {
    val out = sbtProcessWithOpts("-version", "", "").!!.trim
    val expectedVersion = "^sbt version in this project: \\d\\.\\d+\\.\\d+\\S*$"
    assert(out.matches(expectedVersion))

    val out2 = sbtProcessWithOpts("-V", "", "").!!.trim
    assert(out2.matches(expectedVersion))
    ()
  }

}
