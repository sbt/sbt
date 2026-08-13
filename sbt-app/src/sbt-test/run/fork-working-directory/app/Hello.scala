package example

import java.io.File
import java.nio.file.{ Files, Path }

@main
def hello(arg: String*): Unit =
  val x = new File(".").getAbsolutePath
  println(s"hi $x")
  Files.createFile(Path.of("flag"))
