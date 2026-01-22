import java.nio.file.{Files, Path, Paths}
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import scala.jdk.CollectionConverters._
import ujson._
import org.scalatest.funsuite.AnyFunSuite

class BspConfigLauncherTest extends AnyFunSuite {

  test("generate .bsp/sbt.json and execute generated argv") {
    val projectDir: Path = Paths.get("sbt-app/src/sbt-test/bsp/bsp-config-launcher/test")
    val bspFile = projectDir.resolve(".bsp").resolve("sbt.json")

    val genProcess = new ProcessBuilder("sbt", "bspConfig")
      .directory(projectDir.toFile)
      .redirectErrorStream(true)
      .start()

    val genFinished = genProcess.waitFor(120, TimeUnit.SECONDS)
    assert(genFinished && genProcess.exitValue() == 0, "bspConfig generation failed or timed out")

    assert(Files.exists(bspFile), s".bsp/sbt.json not found: $bspFile")
    val jsonText = new String(Files.readAllBytes(bspFile), StandardCharsets.UTF_8)
    val json = ujson.read(jsonText)

    assert(json.obj.contains("argv"), ".bsp/sbt.json missing 'argv' key")
    val argv = json("argv").arr.map(_.str)
    assert(argv.nonEmpty, "argv must be a non-empty array")
    assert(jsonText.endsWith("\n"), ".bsp/sbt.json must end with a newline")

    val pb = new ProcessBuilder(argv.asJava)
    pb.directory(projectDir.toFile)
    pb.redirectErrorStream(true)
    val proc = pb.start()

    val finished = proc.waitFor(120, TimeUnit.SECONDS)
    assert(finished, "Generated argv process timed out")
    assert(proc.exitValue() == 0, s"Generated argv process exited with ${proc.exitValue()}")
  }
}
