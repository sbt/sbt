import java.io.File
import java.nio.file.Files

import scala.util.Try

object Main {

  def classFound(clsName: String) = Try(
    Thread.currentThread()
      .getContextClassLoader()
      .loadClass(clsName)
  ).toOption.nonEmpty

  def main(args: Array[String]): Unit = {
    val ioFound = classFound("cats.effect.IO")
    val asyncFound = classFound("cats.effect.kernel.Async")
    val mutexFound = classFound("cats.effect.std.Mutex")
    val askFound = classFound("cats.mtl.Ask")

    assert(
      ioFound,
      "Expected to find class from cats-effect"
    )
    assert(
      !asyncFound,
      "Expected not to find class from cats-effect-kernel"
    )
    assert(
      !mutexFound,
      "Expected not to find class from cats-effect-std"
    )
    assert(
      !askFound,
      "Expected not to find class from cats-mtl"
    )

    Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
  }
}
