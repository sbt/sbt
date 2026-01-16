package example.test

import scala.sys.process.*
import java.io.File
import java.util.Locale
import sbt.io.IO
import verify.BasicTestSuite

object ExtendedRunnerTest extends BasicTestSuite:
  // 1.3.0, 1.3.0-M4
  private[test] val versionRegEx = "\\d(\\.\\d+){2}(-\\w+)?"

  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  lazy val isMac: Boolean = sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("mac")
  lazy val sbtScript =
    if (isWindows) new File("launcher-package/target/universal/stage/bin/sbt.bat")
    else new File("launcher-package/target/universal/stage/bin/sbt")

  def sbtProcess(args: String*) = sbtProcessWithOpts(args*)("", "")
  def sbtProcessWithOpts(args: String*)(javaOpts: String, sbtOpts: String) =
    Process(
      Seq(sbtScript.getAbsolutePath) ++ args,
      new File("launcher-package/citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts
    )
  def sbtProcessInDir(dir: File)(args: String*) =
    Process(
      Seq(sbtScript.getAbsolutePath) ++ args,
      dir,
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> ""
    )

  test("sbt runs") {
    assert(sbtScript.exists)
    val out = sbtProcess("compile", "-v").!
    assert(out == 0)
    ()
  }

  def testVersion(lines: List[String]): Unit = {
    assert(lines.size >= 2)
    val expected0 = s"(?m)^sbt version in this project: $versionRegEx(\\r)?"
    assert(lines(0).matches(expected0))
    val expected1 = s"sbt runner version: $versionRegEx$$"
    assert(lines(1).matches(expected1))
  }

  /* TODO: The lines seems to return List([0Jsbt runner version: 1.11.4) on CI
  test("sbt -V|-version|--version should print sbtVersion") {
    val out = sbtProcess("-version").!!.trim
    testVersion(out.linesIterator.toList)

    val out2 = sbtProcess("--version").!!.trim
    testVersion(out2.linesIterator.toList)

    val out3 = sbtProcess("-V").!!.trim
    testVersion(out3.linesIterator.toList)
  }
   */

  test("sbt -V in empty directory") {
    IO.withTemporaryDirectory { tmp =>
      val out = sbtProcessInDir(tmp)("-V").!!.trim
      val expectedVersion = "^" + versionRegEx + "$"
      val targetDir = new File(tmp, "target")
      assert(!targetDir.exists, "expected target directory to not exist, but existed")
    }
    ()
  }

  /* TODO: Not sure why but the output is returning [0J on CI
  test("sbt --numeric-version should print sbt script version") {
    val out = sbtProcess("--numeric-version").!!.trim
    val expectedVersion = "^"+versionRegEx+"$"
    assert(out.matches(expectedVersion))
    ()
  }
   */

  test("sbt --sbt-jar should run") {
    val out = sbtProcess(
      "compile",
      "-v",
      "--sbt-jar",
      "../target/universal/stage/bin/sbt-launch.jar"
    ).!!.linesIterator.toList
    assert(
      out.contains[String]("../target/universal/stage/bin/sbt-launch.jar") ||
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
      val expectedVersion = "^" + versionRegEx + "$"
      assert(out.matches(expectedVersion))
    }
    ()
  }

  test("sbt --jvm-client") {
    val out = sbtProcess("--jvm-client", "--no-colors", "compile").!!.linesIterator.toList
    if (isWindows) {
      println(out)
    } else {
      assert(out.exists { _.contains("server was not detected") })
    }
    val out2 = sbtProcess("--jvm-client", "--no-colors", "shutdown").!!.linesIterator.toList
    if (isWindows) {
      println(out2)
    } else {
      assert(out2.exists { _.contains("disconnected") })
    }
    ()
  }
end ExtendedRunnerTest
