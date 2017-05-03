import java.io.File
import java.nio.file.Files

import argonaut._

object Main extends App {

  def excludeCheck(): Unit = {
    val className = "shapeless.HList"
    val loader = Thread.currentThread.getContextClassLoader

    val found =
      try {
        loader.loadClass(className)
        true
      } catch {
        case _: java.lang.ClassNotFoundException => false
      }

    assert(!found, s"Expected class $className not to be found")
  }

  excludeCheck()

  val expectedClassName =
    if (args.contains("--shaded"))
      "test.shaded.argonaut.Json"
    else
      // Don't use the literal "argonaut.Json", that seems to get
      // changed to "test.shaded.argonaut.Json" by shading
      "argonaut" + '.' + "Json"

  val className = classOf[Json].getName

  Console.err.println(s"Expected class name: $expectedClassName")
  Console.err.println(s"Class name: $className")

  if (className != expectedClassName)
    sys.error(s"Expected class name $expectedClassName, got $className")

  val msg = Json.obj().nospaces

  Files.write(new File("output").toPath, msg.getBytes("UTF-8"))
}
