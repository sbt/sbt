package example.test

import minitest._
import sbt.io.IO

import java.io.File
import java.io.PrintWriter
import java.nio.file.Files

object SbtScriptTest extends SimpleTestSuite with PowerAssertions {
  lazy val isWindows: Boolean =
    sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")
  lazy val sbtScript =
    if (isWindows) new File("target/universal/stage/bin/sbt.bat")
    else new File("target/universal/stage/bin/sbt")

  private val javaBinDir = new File("integration-test", "bin").getAbsolutePath

  private def makeTest(
      name: String,
      javaOpts: String = "",
      sbtOpts: String = "",
      sbtOptsFileContents: String = "",
      javaToolOptions: String = ""
  )(args: String*)(f: List[String] => Any) = {
    test(name) {
      val workingDirectory = Files.createTempDirectory("sbt-launcher-package-test").toFile
      IO.copyDirectory(new File("citest"), workingDirectory)

      try {
        val sbtOptsFile = new File(workingDirectory, ".sbtopts")
        sbtOptsFile.createNewFile()
        val writer = new PrintWriter(sbtOptsFile)
        try {
          writer.write(sbtOptsFileContents)
        } finally {
          writer.close()
        }
        val path = sys.env.getOrElse("PATH", sys.env("Path"))
        val out = sbt.internal.Process(
          Seq(sbtScript.getAbsolutePath) ++ args,
          workingDirectory,
          "JAVA_OPTS" -> javaOpts,
          "SBT_OPTS" -> sbtOpts,
          "JAVA_TOOL_OPTIONS" -> javaToolOptions,
          if (isWindows)
            "JAVACMD" -> new File(javaBinDir, "java").getAbsolutePath()
          else
            "PATH" -> (javaBinDir + File.pathSeparator + path)
        ).!!.linesIterator.toList
        f(out)
        ()
      } finally {
        IO.delete(workingDirectory)
      }
    }
  }

  makeTest("sbt -no-colors")("compile", "-no-colors", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
  }

  makeTest("sbt --no-colors")("compile", "--no-colors", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
  }

  makeTest("sbt --color=false")("compile", "--color=false", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.color=false"))
  }

  makeTest("sbt --no-colors in SBT_OPTS", sbtOpts = "--no-colors")("compile", "-v") {
    out: List[String] =>
      if (isWindows) cancel("Test not supported on windows")
      assert(out.contains[String]("-Dsbt.log.noformat=true"))
  }

  makeTest("sbt --no-server")("compile", "--no-server", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.server.autostart=false"))
  }

  makeTest("sbt --debug-inc")("compile", "--debug-inc", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dxsbt.inc.debug=true"))
  }

  makeTest("sbt --supershell=never")("compile", "--supershell=never", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.supershell=never"))
  }

  makeTest("sbt --timings")("compile", "--timings", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.task.timings=true"))
  }

  makeTest("sbt -D arguments")("-Dsbt.supershell=false", "compile", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.supershell=false"))
  }

  makeTest("sbt --sbt-version")("--sbt-version", "1.3.13", "-v") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.version=1.3.13"))
  }

  makeTest("sbt -mem 503")("-mem", "503", "-v") { out: List[String] =>
    assert(out.contains[String]("-Xmx503m"))
  }

  makeTest("sbt with -mem 503, -Xmx in JAVA_OPTS", javaOpts = "-Xmx1024m")("-mem", "503", "-v") {
    out: List[String] =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xmx1024m"))
  }

  makeTest("sbt with -mem 503, -Xmx in SBT_OPTS", sbtOpts = "-Xmx1024m")("-mem", "503", "-v") {
    out: List[String] =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xmx1024m"))
  }

  makeTest("sbt with -mem 503, -Xss in JAVA_OPTS", javaOpts = "-Xss6m")("-mem", "503", "-v") {
    out: List[String] =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xss6m"))
  }

  makeTest("sbt with -mem 503, -Xss in SBT_OPTS", sbtOpts = "-Xss6m")("-mem", "503", "-v") {
    out: List[String] =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xss6m"))
  }

  makeTest(
    "sbt with -Xms2048M -Xmx2048M -Xss6M in JAVA_OPTS",
    javaOpts = "-Xms2048M -Xmx2048M -Xss6M"
  )("-v") { out: List[String] =>
    assert(out.contains[String]("-Xms2048M"))
    assert(out.contains[String]("-Xmx2048M"))
    assert(out.contains[String]("-Xss6M"))
  }

  makeTest(
    "sbt with -Xms2048M -Xmx2048M -Xss6M in SBT_OPTS",
    sbtOpts = "-Xms2048M -Xmx2048M -Xss6M"
  )("-v") { out: List[String] =>
    assert(out.contains[String]("-Xms2048M"))
    assert(out.contains[String]("-Xmx2048M"))
    assert(out.contains[String]("-Xss6M"))
  }

  makeTest(
    name = "sbt with -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 in SBT_OPTS",
    sbtOpts = "-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080",
  )("-v") { out: List[String] =>
    assert(out.contains[String]("-Dhttp.proxyHost=proxy"))
    assert(out.contains[String]("-Dhttp.proxyPort=8080"))
  }

  makeTest(
    name = "sbt with -XX:ParallelGCThreads=16 -XX:PermSize=128M in SBT_OPTS",
    sbtOpts = "-XX:ParallelGCThreads=16 -XX:PermSize=128M",
  )("-v") { out: List[String] =>
    assert(out.contains[String]("-XX:ParallelGCThreads=16"))
    assert(out.contains[String]("-XX:PermSize=128M"))
  }

  makeTest(
    "sbt with -XX:+UseG1GC -XX:+PrintGC in JAVA_OPTS",
    javaOpts = "-XX:+UseG1GC -XX:+PrintGC"
  )("-v") { out: List[String] =>
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))
  }

  makeTest("sbt with -XX:-UseG1GC -XX:-PrintGC in SBT_OPTS", sbtOpts = "-XX:+UseG1GC -XX:+PrintGC")(
    "-v"
  ) { out: List[String] =>
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))
  }

  makeTest("sbt with -debug in SBT_OPTS appears in sbt commands", javaOpts = "", sbtOpts = "-debug")("compile", "-v") {out: List[String] =>
    if (isWindows) cancel("Test not supported on windows")

    // Debug argument must appear in the 'commands' section (after the sbt-launch.jar argument) to work
    val sbtLaunchMatcher = """^.+sbt-launch.jar["]{0,1}$""".r
    val locationOfSbtLaunchJarArg = out.zipWithIndex.collectFirst {
      case (arg, index) if sbtLaunchMatcher.findFirstIn(arg).nonEmpty => index
    }

    assert(locationOfSbtLaunchJarArg.nonEmpty)

    val argsAfterSbtLaunch = out.drop(locationOfSbtLaunchJarArg.get)
    assert(argsAfterSbtLaunch.contains("-debug"))
    ()
  }

  makeTest("sbt --jvm-debug <port>")("--jvm-debug", "12345", "-v") { out: List[String] =>
    assert(
      out.contains[String]("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=12345")
    )
  }

  makeTest("sbt --no-share adds three system properties")("--no-share") { out: List[String] =>
    assert(out.contains[String]("-Dsbt.global.base=project/.sbtboot"))
    assert(out.contains[String]("-Dsbt.boot.directory=project/.boot"))
    assert(out.contains[String]("-Dsbt.ivy.home=project/.ivy"))
  }

  makeTest("accept `--ivy` in `SBT_OPTS`", sbtOpts = "--ivy /ivy/dir")("-v") { out: List[String] =>
    if (isWindows) cancel("Test not supported on windows")
    assert(out.contains[String]("-Dsbt.ivy.home=/ivy/dir"))
  }

  makeTest("sbt --script-version should print sbtVersion")("--script-version") { out: List[String] =>
    val expectedVersion = "^" + SbtRunnerTest.versionRegEx + "$"
    assert(out.mkString(System.lineSeparator()).trim.matches(expectedVersion))
    ()
  }

  makeTest("--sbt-cache")("--sbt-cache", "./cachePath") { out: List[String] =>
    assert(out.contains[String](s"-Dsbt.global.localcache=./cachePath"))
  }

  makeTest(
    "sbt use .sbtopts file for memory options", sbtOptsFileContents =
      """-J-XX:MaxInlineLevel=20
        |-J-Xmx222m
        |-J-Xms111m
        |-J-Xss12m""".stripMargin

  )("compile", "-v") { out: List[String] =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))
  }

  makeTest(
    "sbt use JAVA_OPTS for memory options", javaOpts = "-XX:MaxInlineLevel=20 -Xmx222m -Xms111m -Xss12m"
  )("compile", "-v") { out: List[String] =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))
  }

  makeTest(
    "sbt use JAVA_TOOL_OPTIONS for memory options", javaToolOptions = "-XX:MaxInlineLevel=20 -Xmx222m -Xms111m -Xss12m"
  )("compile", "-v") { out: List[String] =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))
  }
}
