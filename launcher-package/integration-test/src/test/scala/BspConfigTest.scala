package example.test

import scala.concurrent.duration.*
import scala.sys.process.*
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.util.Locale
import sbt.io.IO
import verify.BasicTestSuite

// Test for issues #7792/#7794: BSP config generation and argv execution
object BspConfigTest extends BasicTestSuite:
  lazy val isWindows: Boolean =
    sys.props("os.name").toLowerCase(Locale.ENGLISH).contains("windows")
  lazy val sbtScript = IntegrationTestPaths.sbtScript(isWindows)

  private def launcherCmd = LauncherTestHelper.launcherCommand(sbtScript.getAbsolutePath)

  private val BspTimeout = 3.minutes
  private val ResultMarker = "\"result\""

  def sbtProcessInDir(dir: File)(args: String*) =
    Process(
      launcherCmd ++ args,
      dir,
      "JAVA_OPTS" -> "",
      "SBT_OPTS" -> ""
    )

  test("sbt bspConfig") {
    import ujson.*

    IO.withTemporaryDirectory { tmp =>
      // Create minimal build.sbt for the test project
      IO.write(new File(tmp, "build.sbt"), """name := "test-bsp-config"""")

      // Run bspConfig to generate .bsp/sbt.json
      val configResult = sbtProcessInDir(tmp)("bspConfig", "--batch").!
      assert(configResult == 0, s"bspConfig command failed with exit code $configResult")

      // Verify .bsp/sbt.json exists
      val bspFile = new File(tmp, ".bsp/sbt.json")
      assert(bspFile.exists, ".bsp/sbt.json should exist after running bspConfig")

      // Parse and verify JSON content
      val content = IO.read(bspFile)
      val json = ujson.read(content)

      // Extract argv array from JSON
      val argvValue = json.obj.get("argv")
      assert(argvValue.isDefined, "argv field not found in sbt.json")

      val argv = argvValue.get.arr.map(_.str).toVector

      // Verify argv structure
      assert(argv.nonEmpty, "argv should not be empty")
      // When sbt script is available, argv uses the sbt script with "bsp" command.
      // When not, argv falls back to direct java invocation with "-bsp" flag.
      val usesSbtScript = argv.last == "bsp" && !argv.head.contains("java")
      val usesJavaDirect = argv.head.contains("java") && argv.contains("-bsp")
      assert(
        usesSbtScript || usesJavaDirect,
        s"argv should either use sbt script with 'bsp' command or java with '-bsp' flag, got: $argv"
      )

      assertBspInitialize(argv, tmp)
    }
    ()
  }

  // The argv above only goes through the sbt script when the build runs sbt 2.x,
  // so drive the script with the `bsp` command directly to cover older versions too.
  test("sbt bsp") {
    IO.withTemporaryDirectory { tmp =>
      IO.write(new File(tmp, "build.sbt"), """name := "test-bsp"""")
      assertBspInitialize((launcherCmd ++ Seq("bsp")).toVector, tmp)
    }
    ()
  }

  private def assertBspInitialize(argv: Vector[String], dir: File): Unit =
    val response = bspInitialize(argv, dir)
    assert(
      response.contains(ResultMarker) && response.contains("bspVersion"),
      s"${argv.mkString(" ")} did not answer build/initialize, read: $response"
    )

  private def bspInitialize(argv: Vector[String], dir: File): String =
    val body = ujson
      .write(
        ujson.Obj(
          "jsonrpc" -> "2.0",
          "id" -> 1,
          "method" -> "build/initialize",
          "params" -> ujson.Obj(
            "displayName" -> "sbt-launcher-integration-test",
            "version" -> "1.0.0",
            "bspVersion" -> "2.1.0-M1",
            "rootUri" -> dir.toURI.toString,
            "capabilities" -> ujson.Obj("languageIds" -> ujson.Arr("scala")),
          )
        )
      )
      .getBytes(UTF_8)
    val process = new java.lang.ProcessBuilder(argv*).directory(dir).start()
    try
      val stdin = process.getOutputStream
      stdin.write(s"Content-Length: ${body.length}\r\n\r\n".getBytes(UTF_8))
      stdin.write(body)
      stdin.flush()
      val stdout = process.getInputStream
      val buffer = new Array[Byte](4096)
      val out = new StringBuilder
      val deadline = System.currentTimeMillis + BspTimeout.toMillis
      var done = false
      while !done && System.currentTimeMillis < deadline do
        if stdout.available > 0 then
          val n = stdout.read(buffer)
          if n > 0 then out ++= new String(buffer, 0, n, UTF_8) else done = true
        else if !process.isAlive then done = true
        else Thread.sleep(50)
        done = done || out.indexOf(ResultMarker) >= 0
      out.toString
    finally
      process.descendants.forEach: handle =>
        handle.destroy()
        ()
      process.destroy()
    end try
  end bspInitialize

end BspConfigTest
