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
  val argonautFound = classFound("argonaut.Json")
  val argonautShapelessFound = classFound("argonaut.derive.MkEncodeJson")

  assert(
    argonautShapelessFound,
    "Expected to find class from argonaut-shapeless"
  )
  assert(
    !shapelessFound,
    "Expected not to find classes from shapeless"
  )
  assert(
    !argonautFound,
    "Expected not to find classes from argonaut"
  )
}
