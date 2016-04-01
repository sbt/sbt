import java.io.File
import java.nio.file.Files

import scala.util.Try

object Main extends App {

  def classFound(clsName: String) = Try(
    Thread.currentThread()
      .getContextClassLoader()
      .loadClass(clsName)
  ).toOption.nonEmpty

  val shapelessFound = classFound("shapeless.HList")
  val argonautShapelessFound = classFound("argonaut.derive.MkEncodeJson")

  assert(argonautShapelessFound)
  assert(!shapelessFound)

  Files.write(new File("output").toPath, "OK".getBytes("UTF-8"))
}
