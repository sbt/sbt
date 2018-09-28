import java.io.File
import java.nio.file.Files

import argonaut._

import Foo._

object Main extends App {

  val expectedClassName0 = expectedClassName(args.headOption == Some("--shaded"))

  Console.err.println(s"Expected class name: $expectedClassName0")
  Console.err.println(s"Class name: $className")

  if (className != expectedClassName0)
    sys.error(s"Expected class name $expectedClassName0, got $className")

  val msg = Json.obj().nospaces

  Files.write(new File("output").toPath, msg.getBytes("UTF-8"))
}
