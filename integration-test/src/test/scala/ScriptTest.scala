package example.test

import minitest._
import java.io.File

object SbtScriptTest extends SimpleTestSuite with PowerAssertions {
  lazy val isWindows: Boolean = sys.props("os.name").toLowerCase(java.util.Locale.ENGLISH).contains("windows")
  lazy val sbtScript =
    if (isWindows) new File("target/universal/stage/bin/sbt.bat")
    else new File("target/universal/stage/bin/sbt")

  private val javaBinDir = new File("integration-test", "bin").getAbsolutePath

  def sbtProcess(args: String*) = sbtProcessWithOpts(args: _*)("", "")
  def sbtProcessWithOpts(args: String*)(javaOpts: String, sbtOpts: String) = {
    val path = sys.env("PATH")
    sbt.internal.Process(Seq(sbtScript.getAbsolutePath) ++ args, new File("citest"),
      "JAVA_OPTS" -> javaOpts,
      "SBT_OPTS" -> sbtOpts,
      if (isWindows)
        "JAVACMD" -> new File(javaBinDir, "java.cmd").getAbsolutePath()
      else 
        "PATH" -> (javaBinDir + File.pathSeparator + path)
    )
  }

  test("sbt -no-colors") {
    val out = sbtProcess("compile", "-no-colors").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --no-colors") {
    val out = sbtProcess("compile", "--no-colors").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt --color=false") {
    val out = sbtProcess("compile", "--color=false", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.color=false"))
    ()
  }

  test("sbt --debug-inc") {
    val out = sbtProcess("compile", "--debug-inc", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dxsbt.inc.debug=true"))
    ()
  }

  test("sbt --supershell=never") {
    val out = sbtProcess("compile", "--supershell=never", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.supershell=never"))
    ()
  }

  test("sbt --timings") {
    val out = sbtProcess("compile", "--timings", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.task.timings=true"))
    ()
  }

  test("sbt -D arguments") {
    val out = sbtProcess("-Dsbt.supershell=false", "compile", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.supershell=false"))
    ()
  }

  test("sbt --sbt-version") {
    val out = sbtProcess("--sbt-version", "1.3.13", "compile", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.version=1.3.13"))
    ()
  }

  test("sbt -mem 503") {
    val out = sbtProcess("compile", "-mem", "503", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    ()
  }

  test("sbt with -mem 503, -Xmx in JAVA_OPTS") {
    val out = sbtProcessWithOpts("compile", "-mem", "503", "-v")("-Xmx1024m", "").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    assert(!out.contains[String]("-Xmx1024m"))
    ()
  }

  test("sbt with -mem 503, -Xmx in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-mem", "503", "-v")("", "-Xmx1024m").!!.linesIterator.toList
    assert(out.contains[String]("-Xmx503m"))
    assert(!out.contains[String]("-Xmx1024m"))
    ()
  }

  test("sbt with -Xms2048M -Xmx2048M -Xss6M in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-v")("", "-Xms2048M -Xmx2048M -Xss6M").!!.linesIterator.toList
    assert(out.contains[String]("-Xss6M"))
    ()
  }

  test("sbt with -Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080 in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-v")("", "-Dhttp.proxyHost=proxy -Dhttp.proxyPort=8080").!!.linesIterator.toList
    assert(out.contains[String]("-Dhttp.proxyHost=proxy"))
    assert(out.contains[String]("-Dhttp.proxyPort=8080"))
    ()
  }

  test("sbt with -XX:ParallelGCThreads=16 -XX:PermSize=128M in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-v")("", "-XX:ParallelGCThreads=16 -XX:PermSize=128M").!!.linesIterator.toList
    assert(out.contains[String]("-XX:ParallelGCThreads=16"))
    assert(out.contains[String]("-XX:PermSize=128M"))
    ()
  }

  test("sbt with -XX:+UseG1GC -XX:+PrintGC in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-v")("", "-XX:+UseG1GC -XX:+PrintGC").!!.linesIterator.toList
    assert(out.contains[String]("-XX:+UseG1GC"))
    assert(out.contains[String]("-XX:+PrintGC"))
    assert(!out.contains[String]("-XX:+UseG1GC=-XX:+PrintGC"))
    ()
  }

  test("sbt with -XX:-UseG1GC -XX:-PrintGC in SBT_OPTS") {
    val out = sbtProcessWithOpts("compile", "-v")("", "-XX:-UseG1GC -XX:-PrintGC").!!.linesIterator.toList
    assert(out.contains[String]("-XX:-UseG1GC"))
    assert(out.contains[String]("-XX:-PrintGC"))
    assert(!out.contains[String]("-XX:-UseG1GC=-XX:-PrintGC"))
    ()
  }

  test("sbt with --no-colors in SBT_OPTS") {
    if (isWindows) cancel("Test not supported on windows")
    val out = sbtProcessWithOpts("compile", "-v")("", "--no-colors").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.log.noformat=true"))
    ()
  }

  test("sbt with -debug in SBT_OPTS appears in sbt commands") {
    if (isWindows) cancel("Test not supported on windows")

    val out: List[String] = sbtProcessWithOpts("compile", "-v")("", "-debug").!!.linesIterator.toList
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

  test("sbt --jvm-debug <port>") {
    val out = sbtProcess("--jvm-debug", "12345", "compile", "-v").!!.linesIterator.toList
    assert(out.contains[String]("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=12345"))
    ()
  }

  test("sbt --no-share adds three system properties") {
    val out = sbtProcess("--no-share").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.global.base=project/.sbtboot"))
    assert(out.contains[String]("-Dsbt.boot.directory=project/.boot"))
    assert(out.contains[String]("-Dsbt.ivy.home=project/.ivy"))
    ()
  }

  test("accept `--ivy` in `SBT_OPTS`") {
    if (isWindows) cancel("Test not supported on windows")
    val out = sbtProcessWithOpts("")("", sbtOpts = "--ivy /ivy/dir").!!.linesIterator.toList
    assert(out.contains[String]("-Dsbt.ivy.home=/ivy/dir"))
    ()
  }
}
