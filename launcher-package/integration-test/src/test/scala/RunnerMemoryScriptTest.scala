package example.test

/**
 * RunnerMemoryScriptTest is used to test the sbt shell script, for both macOS/Linux and Windows.
 */
object RunnerMemoryScriptTest extends verify.BasicTestSuite with ShellScriptUtil:

  testOutput("sbt -mem 503")("-mem", "503", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-Xmx503m"))

  testOutput("sbt with -mem 503, -Xmx in JAVA_OPTS", javaOpts = "-Xmx1024m")("-mem", "503", "-v"):
    (out: List[String]) =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xmx1024m"))

  testOutput("sbt with -mem 503, -Xmx in SBT_OPTS", sbtOpts = "-Xmx1024m")("-mem", "503", "-v"):
    (out: List[String]) =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xmx1024m"))

  testOutput("sbt with -mem 503, -Xss in JAVA_OPTS", javaOpts = "-Xss6m")("-mem", "503", "-v"):
    (out: List[String]) =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xss6m"))

  testOutput("sbt with -mem 503, -Xss in SBT_OPTS", sbtOpts = "-Xss6m")("-mem", "503", "-v"):
    (out: List[String]) =>
      assert(out.contains[String]("-Xmx503m"))
      assert(!out.contains[String]("-Xss6m"))

  testOutput(
    "sbt with -Xms2048M -Xmx2048M -Xss6M in JAVA_OPTS",
    javaOpts = "-Xms2048M -Xmx2048M -Xss6M"
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("-Xms2048M"))
    assert(out.contains[String]("-Xmx2048M"))
    assert(out.contains[String]("-Xss6M"))

  testOutput(
    "sbt with -Xms2048M -Xmx2048M -Xss6M in SBT_OPTS",
    sbtOpts = "-Xms2048M -Xmx2048M -Xss6M"
  )("-v"): (out: List[String]) =>
    assert(out.contains[String]("-Xms2048M"))
    assert(out.contains[String]("-Xmx2048M"))
    assert(out.contains[String]("-Xss6M"))

  testOutput(
    "sbt use .sbtopts file for memory options",
    sbtOptsFileContents = """-J-XX:MaxInlineLevel=20
        |-J-Xmx222m
        |-J-Xms111m
        |-J-Xss12m""".stripMargin
  )("compile", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))

  testOutput(
    "sbt use JAVA_OPTS for memory options",
    javaOpts = "-XX:MaxInlineLevel=20 -Xmx222m -Xms111m -Xss12m"
  )("compile", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))

  testOutput(
    "sbt use JAVA_TOOL_OPTIONS for memory options",
    javaToolOptions = "-XX:MaxInlineLevel=20 -Xmx222m -Xms111m -Xss12m"
  )("compile", "-v"): (out: List[String]) =>
    assert(out.contains[String]("-XX:MaxInlineLevel=20"))
    assert(out.contains[String]("-Xmx222m"))
    assert(out.contains[String]("-Xms111m"))
    assert(out.contains[String]("-Xss12m"))

end RunnerMemoryScriptTest
