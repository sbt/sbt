import java.io.File
import java.nio.file.Files

import argonaut._, Argonaut._, ArgonautShapeless._

object Main extends App {

  case class CC(i: Int, s: String)

  val msg = CC(2, A.msg).asJson.spaces2

  Files.write(new File("output").toPath, msg.getBytes("UTF-8"))
}
