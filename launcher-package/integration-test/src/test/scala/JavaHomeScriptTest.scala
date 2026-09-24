/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package example.test

import java.io.File

/**
 * First-hop propagation guard: an explicit `-java-home` must be the JDK the Windows launcher runs
 * (echoed on the verbose command line). Not the full sbt/sbt#963 regression — the
 * `.java-version`-override case needs two JDK paths + the native client, so it is verified manually
 * on Windows.
 */
object JavaHomeScriptTest extends verify.BasicTestSuite with ShellScriptUtil:
  private val jdkHome =
    new File(sys.env.getOrElse("JAVA_HOME", System.getProperty("java.home"))).getAbsolutePath

  testOutput("sbt -java-home selects the JDK for the launcher")(
    "-java-home",
    jdkHome,
    "compile",
    "-v"
  ): (out: List[String]) =>
    if !isWindows then cancel("`-java-home` bin/java.exe selection is Windows-specific")
    else
      val javaExe = new File(new File(jdkHome, "bin"), "java.exe").getAbsolutePath
      assert(
        out.exists(_.contains(javaExe)),
        s"launcher should run $javaExe; command echo was: ${out.mkString(" | ")}"
      )
end JavaHomeScriptTest
