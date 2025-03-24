package example.test

import minitest._
import scala.sys.process._
import java.io.File
import java.util.Locale
import sbt.io.IO

object SbtRunnerTest extends SimpleTestSuite with PowerAssertions {
  // 1.3.0, 1.3.0-M4
  private[test] val versionRegEx = "\\d(\\.\\d+){2}(-\\w+)?"

  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  lazy val isMac: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
  lazy val sbtScript =
    if (isWindows) new File("target/universal/stage/bin/sbt.bat")
    else new File("target/universal/stage/bin/sbt")

  def sbtProcess(args: String*) = sbtProcessWithOpts(args: _*)("", "")
  def sbtProcessWithOpts(args: String*)(javaOpts: String, sbtOpts: String) =
    sbt.internal.Process(Seq(sbtScript.getAbsolutePath) ++ args, new File("citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts)
  def sbtProcessInDir(dir: File)(args: String*) =
    sbt.internal.Process(Seq(sbtScript.getAbsolutePath) ++ args, dir,
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> "")

  test("sbt runs") {
    assert(sbtScript.exists)
    val out = sbtProcess("compile", "-v").!
    assert(out == 0)
    ()
  }

  test("sbt -V|-version|--version should print sbtVersion") {
    val out = sbtProcess("-version").!!.trim
    val expectedVersion =
      s"""|(?m)^sbt version in this project: $versionRegEx(\\r)?
          |sbt script version: $versionRegEx$$
          |""".stripMargin.trim.replace("\n", "\\n")
    assert(out.matches(expectedVersion))

    val out2 = sbtProcess("--version").!!.trim
    assert(out2.matches(expectedVersion))

    val out3 = sbtProcess("-V").!!.trim
    assert(out3.matches(expectedVersion))
    ()
  }

  test("sbt --numeric-version should print sbt script version") {
    val out = sbtProcess("--numeric-version").!!.trim
    val expectedVersion = "^"+versionRegEx+"$"
    assert(out.matches(expectedVersion))
    ()
  }

  test("sbt --sbt-jar should run") {
    val out = sbtProcess("compile", "-v", "--sbt-jar", "../target/universal/stage/bin/sbt-launch.jar").!!.linesIterator.toList
    assert(out.contains[String]("../target/universal/stage/bin/sbt-launch.jar") ||
      out.contains[String]("\"../target/universal/stage/bin/sbt-launch.jar\"")
    )
    ()
  }

  test("sbt \"testOnly *\"") {
    if (isMac) ()
    else {
      val out = sbtProcess("testOnly *", "--no-colors", "-v").!!.linesIterator.toList
      assert(out.contains[String]("[info] HelloTest"))
      ()
    }
  }

  test("sbt in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("about").!
      assert(out == 1)
    }
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("about", "--allow-empty").!
      assert(out == 0)
    }
    ()
  }

  test("sbt --script-version in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("--script-version").!!.trim
      val expectedVersion = "^"+versionRegEx+"$"
      assert(out.matches(expectedVersion))
    }
    ()
  }

  /*
  test("sbt --client") {
    val out = sbtProcess("--client", "--no-colors", "compile").!!.linesIterator.toList
    if (isWindows) {
      println(out)
    } else {
      assert(out exists { _.contains("server was not detected") })
    }
    val out2 = sbtProcess("--client", "--no-colors", "shutdown").!!.linesIterator.toList
    if (isWindows) {
      println(out)
    } else {
      assert(out2 exists { _.contains("disconnected") })
    }
    ()
  }
  */
}
